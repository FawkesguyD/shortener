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

    private final Path configPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConfigService() {
        this(Path.of(System.getProperty("user.home"), ".shortener", "config.json"));
    }

    ConfigService(Path configPath) {
        this.configPath = configPath.toAbsolutePath();
    }

    public Config load() throws IOException {

        Files.createDirectories(configPath.getParent());

        if (Files.notExists(configPath)) {
            Config defaults = new Config(24, 10, 6, "https://clk.ru/", "redis://127.0.0.1:6380");

            Files.writeString(
                    configPath,
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(defaults),
                    StandardOpenOption.CREATE_NEW
            );
        }

        if (!(mapper.readTree(configPath.toFile()) instanceof ObjectNode configJson)) {
            throw new IOException("Expected a configuration object in " + configPath);
        }
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
        if (migrated) {
            Files.writeString(configPath,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config),
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
        return config;
    }
}
