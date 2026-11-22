package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.net.URISyntaxException;

public class Config {

    private final int lifetimeHours;
    private final int defaultMaxClicks;
    private final int keyLength;
    private final String baseUrl;
    private final String redisUrl;

    public Config(@JsonProperty("lifetimeHours") int lifetimeHours,
                  @JsonProperty("defaultMaxClicks") int defaultMaxClicks,
                  @JsonProperty("keyLength") int keyLength,
                  @JsonProperty("baseUrl") String baseUrl,
                  @JsonProperty("redisUrl") String redisUrl) {
        if (lifetimeHours <= 0) {
            throw new IllegalArgumentException("Lifetime must be positive");
        }

        if (defaultMaxClicks <= 0) {
            throw new IllegalArgumentException("Max clicks must be positive");
        }

        if (keyLength < 4 || keyLength > 16) {
            throw new IllegalArgumentException("Key length must be between 4 and 16");
        }

        validateBaseUrl(baseUrl);
        validateRedisUrl(redisUrl);

        this.lifetimeHours = lifetimeHours;
        this.defaultMaxClicks = defaultMaxClicks;
        this.keyLength = keyLength;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.redisUrl = redisUrl;
    }

    public int getLifetimeHours() { return lifetimeHours; }

    public int getDefaultMaxClicks() { return defaultMaxClicks; }

    public int getKeyLength() { return keyLength; }

    public String getBaseUrl() { return baseUrl; }

    public String getRedisUrl() { return redisUrl; }

    private static void validateBaseUrl(String value) {
        URI uri = parseUrl(value, "baseUrl");
        if (uri.getHost() == null || uri.getPort() != -1 || uri.getRawUserInfo() != null ||
                uri.getRawQuery() != null || uri.getRawFragment() != null ||
                !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) ||
                !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) origin, for example https://clk.ru/");
        }
    }

    private static void validateRedisUrl(String value) {
        URI uri = parseUrl(value, "redisUrl");
        if (uri.getHost() == null || uri.getPort() < 1 ||
                !("redis".equalsIgnoreCase(uri.getScheme()) || "rediss".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("redisUrl must be a redis:// or rediss:// URL with a port");
        }
    }

    private static URI parseUrl(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid " + field, e);
        }
    }

    private static String normalizeBaseUrl(String value) {
        return value.endsWith("/") ? value : value + "/";
    }
}
