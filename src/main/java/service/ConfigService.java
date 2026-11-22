package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class ConfigService {

    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".shortener", "config.json");

    private final ObjectMapper mapper = new ObjectMapper();

    public Config load() throws IOException {

        Files.createDirectories(CONFIG_PATH.getParent());

        if (Files.notExists(CONFIG_PATH)) {
            Config defaults = new Config(24, 10, 6, "https://clk.ru/", "redis://127.0.0.1:6380");

            Files.writeString(
                    CONFIG_PATH,
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(defaults),
                    StandardOpenOption.CREATE_NEW
            );
        }

        ObjectNode configJson = (ObjectNode) mapper.readTree(CONFIG_PATH.toFile());
        boolean migrated = false;

        if (!configJson.hasNonNull("baseUrl")) {
            String method = configJson.path("method").asText("https");
            String domain = configJson.path("domain").asText("clk.ru");
            configJson.put("baseUrl", method + "://" + domain + "/");
            configJson.remove(List.of("domain", "method"));
            migrated = true;
        }
        if (!configJson.hasNonNull("redisUrl")) {
            configJson.put("redisUrl", "redis://127.0.0.1:6380");
            migrated = true;
        }

        Config config = mapper.treeToValue(configJson, Config.class);
        if (config == null) {
            throw new IOException("Expected a configuration object in " + CONFIG_PATH);
        }
        if (migrated) {
            Files.writeString(CONFIG_PATH,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config),
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
        return config;
    }
}
