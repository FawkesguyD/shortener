package storage;

import controller.Controller;
import model.Config;
import model.Link;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.RedisClient;
import service.LinkService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RedisStorageIT {
    private static final String OWNER = "owner";
    private static final String OTHER = "other";
    private static final String KEY = "Ab1234";
    private static final String ORIGINAL = "https://example.com/";
    private static final String UPDATED = "https://example.com/updated";
    private static final String HASH_KEY = "shortener:link:" + KEY;
    private static final String OWNER_INDEX = "shortener:user-links:" + OWNER;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.3"))
            .withExposedPorts(6379);

    private static String redisUrl;
    private static RedisClient redis;
    private static RedisStorage storage;

    @BeforeAll
    static void connect() {
        redisUrl = "redis://%s:%d".formatted(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis = RedisClient.create(redisUrl);
        storage = new RedisStorage(redis);
    }

    @AfterAll
    static void disconnect() {
        if (redis != null) {
            redis.close();
        }
    }

    @BeforeEach
    void resetDedicatedContainerDatabase() {
        redis.flushDB();
    }

    @Test
    void registersUserIdempotentlyAndKeepsUsersSeparate() {
        assertFalse(storage.userExists(OWNER));
        storage.createUser(OWNER);
        storage.createUser(OWNER);
        assertTrue(storage.userExists(OWNER));
        assertFalse(storage.userExists(OTHER));
    }

    @Test
    void roundTripsLinkAndRejectsDuplicateKeyWithoutChangingOwnerOrIndex() {
        Instant expires = Instant.now().plusSeconds(60);
        Link saved = new Link(OWNER, ORIGINAL, KEY, 2, 5, Instant.EPOCH, expires);
        assertNull(storage.getLink(KEY));
        assertTrue(storage.saveLinkIfAbsent(saved));
        assertFalse(storage.saveLinkIfAbsent(new Link(OTHER, UPDATED, KEY, 0, 1, Instant.EPOCH, null)));

        Link actual = storage.getLink(KEY);
        assertNotNull(actual);
        assertAll(() -> assertEquals(OWNER, actual.getOwnerId()),
                () -> assertEquals(KEY, actual.getShortKey()),
                () -> assertEquals(ORIGINAL, actual.getOriginalUrl()),
                () -> assertEquals(2, actual.getClickCount()),
                () -> assertEquals(5, actual.getClickMax()),
                () -> assertEquals(Instant.EPOCH, actual.getCreatedAt()),
                () -> assertEquals(expires, actual.getExpiresAt()),
                () -> assertEquals(expires.toEpochMilli(), redis.pexpireTime(HASH_KEY)),
                () -> assertTrue(storage.getUserLinks(OTHER).isEmpty()));
    }

    @Test
    void changesAndRemovesLinkOnlyForOwner() {
        Instant expires = Instant.now().plusSeconds(60);
        assertTrue(storage.saveLinkIfAbsent(new Link(OWNER, ORIGINAL, KEY, 0, 5, Instant.EPOCH, expires)));
        assertFalse(storage.updateLink(OTHER, KEY, UPDATED));
        assertFalse(storage.deleteLink(OTHER, KEY));
        assertEquals(ORIGINAL, storage.getLink(KEY).getOriginalUrl());
        assertTrue(storage.updateLink(OWNER, KEY, UPDATED));
        assertEquals(UPDATED, storage.getLink(KEY).getOriginalUrl());
        assertEquals(expires.toEpochMilli(), redis.pexpireTime(HASH_KEY));
        assertTrue(storage.deleteLink(OWNER, KEY));
        assertNull(storage.getLink(KEY));
        assertNull(redis.zscore(OWNER_INDEX, KEY));
        assertFalse(storage.updateLink(OWNER, KEY, ORIGINAL));
        assertFalse(storage.deleteLink(OWNER, KEY));
    }

    @Test
    void listsOnlyExistingOwnedLinksIncludingLinksWithoutTtl() {
        assertTrue(storage.saveLinkIfAbsent(noExpiryLink(OWNER, KEY, 0, 2)));
        assertTrue(storage.saveLinkIfAbsent(noExpiryLink(OTHER, "Other1", 0, 2)));
        // Simulate stale and mismatched index entries rather than relying on a timing race.
        redis.zadd(OWNER_INDEX, Double.POSITIVE_INFINITY, "Missing1");
        redis.zadd(OWNER_INDEX, Double.POSITIVE_INFINITY, "Other1");

        assertEquals(List.of(KEY), keysFor(OWNER));
        assertEquals(List.of("Other1"), keysFor(OTHER));
        assertNull(storage.getLink(KEY).getExpiresAt());
        assertEquals(-1L, redis.pttl(HASH_KEY));
    }

    @Test
    void incrementsThroughLastAllowedClickWithoutOvershooting() {
        assertNull(storage.resolveLink(KEY));
        assertTrue(storage.saveLinkIfAbsent(noExpiryLink(OWNER, KEY, 0, 2)));
        assertEquals(ORIGINAL, storage.resolveLink(KEY));
        assertEquals(1, storage.getLink(KEY).getClickCount());
        assertEquals(ORIGINAL, storage.resolveLink(KEY));
        assertNull(storage.resolveLink(KEY));
        assertEquals(2, storage.getLink(KEY).getClickCount());
        assertTrue(storage.getLink(KEY).isExpired());
    }

    @Test
    void expiresLinkInRedisAndRemovesItFromUserIndex() throws InterruptedException {
        Instant expires = Instant.now().plusSeconds(2);
        assertTrue(storage.saveLinkIfAbsent(new Link(OWNER, ORIGINAL, KEY, 0, 5, Instant.EPOCH, expires)));
        assertTrue(redis.pttl(HASH_KEY) > 0);
        assertTrue(storage.saveLinkIfAbsent(noExpiryLink(OWNER, "Stable1", 0, 5)));
        assertNotNull(redis.zscore(OWNER_INDEX, KEY));

        long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (storage.getLink(KEY) != null && System.nanoTime() < stop) {
            Thread.sleep(25);
        }

        assertNull(storage.getLink(KEY), "Redis did not expire the link before the deadline");
        assertNull(storage.resolveLink(KEY));
        assertEquals(List.of("Stable1"), keysFor(OWNER));
        assertNull(redis.zscore(OWNER_INDEX, KEY));
    }

    @Test
    void enforcesLimitAcrossConcurrentResolveCalls() throws Exception {
        int callers = 8;
        int max = 5;
        assertTrue(storage.saveLinkIfAbsent(noExpiryLink(OWNER, KEY, max - 2, max)));
        var ready = new CountDownLatch(callers);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(callers);
        var results = new ArrayList<Future<String>>();
        try {
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent callers did not start");
                    }
                    return storage.resolveLink(KEY);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int resolved = 0;
            for (var result : results) {
                String url = result.get(10, TimeUnit.SECONDS);
                if (url != null) {
                    assertEquals(ORIGINAL, url);
                    resolved++;
                }
            }
            assertEquals(2, resolved);
            assertEquals(max, storage.getLink(KEY).getClickCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void runsLinkLifecycleThroughControllerServiceAndRedis() {
        Config config = new Config(24, 2, 6, "https://sho.rt/", redisUrl);
        LinkService service = new LinkService(storage, config);
        Controller owner = new Controller(service, OWNER);
        Controller other = new Controller(service, OTHER);

        String shortUrl = owner.createLink(ORIGINAL, 0);
        Link link = owner.getLink(shortUrl);
        assertEquals(2, link.getClickMax());
        assertEquals(List.of(link.getShortKey()), owner.getUserLinks().stream().map(Link::getShortKey).toList());
        assertTrue(other.getUserLinks().isEmpty());
        assertThrows(SecurityException.class, () -> other.updateLink(shortUrl, UPDATED));
        assertThrows(SecurityException.class, () -> other.deleteLink(shortUrl));
        assertEquals(ORIGINAL, service.resolveLink(shortUrl));
        assertEquals(UPDATED, owner.updateLink(link.getShortKey(), UPDATED).getOriginalUrl());
        assertEquals(UPDATED, service.resolveLink(link.getShortKey()));
        assertThrows(IllegalArgumentException.class, () -> service.resolveLink(shortUrl));
        owner.deleteLink(shortUrl);
        assertTrue(owner.getUserLinks().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> owner.getLink(shortUrl));
    }

    private List<String> keysFor(String user) {
        return storage.getUserLinks(user).stream().map(Link::getShortKey).toList();
    }

    private Link noExpiryLink(String owner, String key, int clickCount, int clickMax) {
        return new Link(owner, ORIGINAL, key, clickCount, clickMax, Instant.EPOCH, null);
    }
}
