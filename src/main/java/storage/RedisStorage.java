package storage;

import model.Link;
import redis.clients.jedis.RedisClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RedisStorage implements LinkRepository, UserRepository {
    private static final String SAVE_LINK = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            redis.call('HSET', KEYS[1], 'ownerId', ARGV[1], 'originalUrl', ARGV[2],
                'clickCount', ARGV[3], 'clickMax', ARGV[4], 'createdAt', ARGV[5], 'expiresAt', ARGV[6])
            if ARGV[6] ~= '' then
                redis.call('PEXPIREAT', KEYS[1], ARGV[7])
            end
            redis.call('ZADD', KEYS[2], ARGV[7], ARGV[8])
            return 1
            """;

    private static final String LIST_KEYS = """
            local now = redis.call('TIME')
            local millis = now[1] * 1000 + math.floor(now[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', millis)
            return redis.call('ZRANGE', KEYS[1], 0, -1)
            """;

    private static final String UPDATE_LINK = """
            if redis.call('HGET', KEYS[1], 'ownerId') ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1], 'originalUrl', ARGV[2])
            return 1
            """;

    private static final String RESOLVE_LINK = """
            local link = redis.call('HMGET', KEYS[1], 'originalUrl', 'clickCount', 'clickMax')
            if not link[1] or tonumber(link[2]) >= tonumber(link[3]) then
                return nil
            end
            redis.call('HINCRBY', KEYS[1], 'clickCount', 1)
            return link[1]
            """;

    private static final String DELETE_LINK = """
            if redis.call('HGET', KEYS[1], 'ownerId') ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[2])
            return 1
            """;

    private final RedisClient redis;

    public RedisStorage(RedisClient redis) {
        this.redis = redis;
    }

    @Override
    public boolean userExists(String userId) {
        return redis.exists("user:" + userId);
    }

    @Override
    public void createUser(String userId) {
        redis.setnx("user:" + userId, "1");
    }

    @Override
    public boolean saveLinkIfAbsent(Link link) {
        Instant expiresAt = link.getExpiresAt();
        Object result = redis.eval(SAVE_LINK,
                List.of(linkKey(link.getShortKey()), userLinksKey(link.getOwnerId())),
                List.of(link.getOwnerId(), link.getOriginalUrl(),
                        Integer.toString(link.getClickCount()), Integer.toString(link.getClickMax()),
                        link.getCreatedAt().toString(), expiresAt == null ? "" : expiresAt.toString(),
                        expiresAt == null ? "+inf" : Long.toString(expiresAt.toEpochMilli()),
                        link.getShortKey()));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public Link getLink(String shortKey) {
        Map<String, String> fields = redis.hgetAll(linkKey(shortKey));
        if (fields.isEmpty()) {
            return null;
        }

        String expiresAt = fields.get("expiresAt");
        return new Link(fields.get("ownerId"), fields.get("originalUrl"), shortKey,
                Integer.parseInt(fields.get("clickCount")), Integer.parseInt(fields.get("clickMax")),
                Instant.parse(fields.get("createdAt")), expiresAt.isEmpty() ? null : Instant.parse(expiresAt));
    }

    @Override
    public String resolveLink(String shortKey) {
        return (String) redis.eval(RESOLVE_LINK, List.of(linkKey(shortKey)), List.of());
    }

    @Override
    public List<Link> getUserLinks(String userId) {
        List<?> keys = (List<?>) redis.eval(LIST_KEYS, List.of(userLinksKey(userId)), List.of());
        List<Link> links = new ArrayList<>(keys.size());
        for (Object key : keys) {
            Link link = getLink((String) key);
            if (link != null && userId.equals(link.getOwnerId())) {
                links.add(link);
            }
        }
        return links;
    }

    @Override
    public boolean updateLink(String userId, String shortKey, String newOriginalUrl) {
        return Long.valueOf(1).equals(redis.eval(UPDATE_LINK,
                List.of(linkKey(shortKey)), List.of(userId, newOriginalUrl)));
    }

    @Override
    public boolean deleteLink(String userId, String shortKey) {
        return Long.valueOf(1).equals(redis.eval(DELETE_LINK,
                List.of(linkKey(shortKey), userLinksKey(userId)), List.of(userId, shortKey)));
    }

    private static String linkKey(String shortKey) {
        return "shortener:link:" + shortKey;
    }

    private static String userLinksKey(String userId) {
        return "shortener:user-links:" + userId;
    }
}
