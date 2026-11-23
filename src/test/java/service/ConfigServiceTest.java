package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {
    @TempDir Path temp;
    private Path file;
    private ConfigService service;

    @BeforeEach
    void setUp() {
        file = temp.resolve(".shortener/config.json");
        service = new ConfigService(file);
    }

    @Test
    void createsDefaultsAndReloadsThem() throws IOException {
        Config config = service.load();
        assertDefaults(config);
        assertTrue(Files.isRegularFile(file));
        assertDefaults(new ConfigService(file).load());
    }

    @Test
    void readsExistingConfigWithoutRewritingIt() throws IOException {
        String json = """
                {"lifetimeHours":1,"defaultMaxClicks":2,"keyLength":4,"baseUrl":"http://custom.test",
                 "redisUrl":"redis://localhost:6379"}
                """;
        write(json);
        Config config = service.load();
        assertAll(() -> assertEquals(1, config.getLifetimeHours()),
                () -> assertEquals(2, config.getDefaultMaxClicks()),
                () -> assertEquals(4, config.getKeyLength()),
                () -> assertEquals("http://custom.test/", config.getBaseUrl()),
                () -> assertEquals("redis://localhost:6379", config.getRedisUrl()),
                () -> assertEquals(json, Files.readString(file)));
    }

    @Test
    void migratesLegacyOriginAndPersistsItWithoutLegacyFields() throws IOException {
        write("""
                {"lifetimeHours":24,"defaultMaxClicks":10,"keyLength":6,"method":"http",
                 "domain":"legacy.test","redisUrl":"redis://localhost:6379"}
                """);
        assertEquals("http://legacy.test/", service.load().getBaseUrl());
        var saved = new ObjectMapper().readTree(file.toFile());
        assertFalse(saved.has("method"));
        assertFalse(saved.has("domain"));
        assertEquals("http://legacy.test/", saved.get("baseUrl").asText());
        assertEquals("redis://localhost:6379", new ConfigService(file).load().getRedisUrl());
    }

    @Test
    void addsDefaultRedisUrlWhenMissing() throws IOException {
        write("""
                {"lifetimeHours":24,"defaultMaxClicks":10,"keyLength":6,"baseUrl":"https://custom.test/"}
                """);
        Config config = service.load();
        assertEquals("https://custom.test/", config.getBaseUrl());
        assertEquals("redis://127.0.0.1:6380", config.getRedisUrl());
        assertEquals(config.getRedisUrl(), new ObjectMapper().readTree(file.toFile()).get("redisUrl").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", "", "{\"lifetimeHours\":0}"})
    void rejectsInvalidConfigurationWithoutOverwritingIt(String json) throws IOException {
        write(json);
        assertThrows(IOException.class, service::load);
        assertEquals(json, Files.readString(file));
    }

    private void write(String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    private void assertDefaults(Config config) {
        assertAll(() -> assertEquals(24, config.getLifetimeHours()),
                () -> assertEquals(10, config.getDefaultMaxClicks()),
                () -> assertEquals(6, config.getKeyLength()),
                () -> assertEquals("https://clk.ru/", config.getBaseUrl()),
                () -> assertEquals("redis://127.0.0.1:6380", config.getRedisUrl()));
    }
}
