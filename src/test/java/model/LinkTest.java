package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LinkTest {
    private static final Instant CREATED = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    @Test
    void exposesFieldsAndRemainsActiveBeforeBothLimits() {
        Link link = link(0, 2, FUTURE);
        assertAll(() -> assertEquals("owner", link.getOwnerId()),
                () -> assertEquals("https://example.com/", link.getOriginalUrl()),
                () -> assertEquals("Ab1234", link.getShortKey()),
                () -> assertEquals(0, link.getClickCount()),
                () -> assertEquals(2, link.getClickMax()),
                () -> assertEquals(CREATED, link.getCreatedAt()),
                () -> assertEquals(FUTURE, link.getExpiresAt()),
                () -> assertFalse(link.isExpired()));
    }

    @ParameterizedTest
    @CsvSource({"-1,2", "0,0", "0,-1"})
    void rejectsInvalidClickCounts(int count, int max) {
        assertThrows(IllegalArgumentException.class, () -> link(count, max, FUTURE));
    }

    @Test
    void rejectsExpiryBeforeCreation() {
        assertThrows(IllegalArgumentException.class, () -> link(0, 2, CREATED.minusSeconds(1)));
    }

    @Test
    void expiresAtClickLimitEvenWithoutTimeLimit() {
        assertFalse(link(1, 2, null).isExpired());
        assertTrue(link(2, 2, null).isExpired());
    }

    @Test
    void expiresByTimeWithClicksRemaining() {
        assertTrue(link(0, 2, CREATED).isExpired());
    }

    private Link link(int count, int max, Instant expires) {
        return new Link("owner", "https://example.com/", "Ab1234", count, max, CREATED, expires);
    }
}
