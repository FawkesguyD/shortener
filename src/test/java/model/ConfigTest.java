package model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    private static final String BASE = "https://sho.rt/";
    private static final String REDIS = "redis://localhost:6379";

    @ParameterizedTest
    @CsvSource({"4, http://sho.rt, redis://localhost:6379, http://sho.rt/",
            "16, https://sho.rt/, rediss://user:pass@localhost:6380, https://sho.rt/"})
    void acceptsBoundariesAndNormalizesBaseUrl(int length, String base, String redis, String expectedBase) {
        Config config = new Config(24, 10, length, base, redis);
        assertAll(() -> assertEquals(24, config.getLifetimeHours()),
                () -> assertEquals(10, config.getDefaultMaxClicks()),
                () -> assertEquals(length, config.getKeyLength()),
                () -> assertEquals(expectedBase, config.getBaseUrl()),
                () -> assertEquals(redis, config.getRedisUrl()));
    }

    @ParameterizedTest
    @CsvSource({"0,10,6", "-1,10,6", "24,0,6", "24,-1,6", "24,10,3", "24,10,17"})
    void rejectsInvalidNumbers(int lifetime, int clicks, int length) {
        assertThrows(IllegalArgumentException.class, () -> new Config(lifetime, clicks, length, BASE, REDIS));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "https://bad host", "/relative", "ftp://sho.rt", "https://sho.rt/path",
            "https://sho.rt:443", "https://sho.rt/?q=1", "https://sho.rt/#fragment", "https://user@sho.rt/"})
    void rejectsInvalidBaseUrl(String base) {
        assertThrows(IllegalArgumentException.class, () -> new Config(24, 10, 6, base, REDIS));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "redis://bad host:6379", "redis:/6379", "redis://localhost",
            "redis://localhost:0", "http://localhost:6379"})
    void rejectsInvalidRedisUrl(String redis) {
        assertThrows(IllegalArgumentException.class, () -> new Config(24, 10, 6, BASE, redis));
    }
}
