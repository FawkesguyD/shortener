package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class Config {

    private final int lifetimeHours;
    private final int defaultMaxClicks;
    private final int keyLength;
    private final String domain;
    private final String method;

    public Config(@JsonProperty("lifetimeHours") int lifetimeHours, @JsonProperty("defaultMaxClicks") int defaultMaxClicks, @JsonProperty("keyLength") int keyLength, @JsonProperty("domain") String domain, @JsonProperty("method") String method) {
        if (lifetimeHours <= 0) {
            throw new IllegalArgumentException("Lifetime must be positive");
        }

        if (defaultMaxClicks <= 0) {
            throw new IllegalArgumentException("Max clicks must be positive");
        }

        if (keyLength < 4 || keyLength > 16) {
            throw new IllegalArgumentException("Key length must be between 4 and 16");
        }

        if (method == null || !method.equalsIgnoreCase("http") && !method.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Method must be http or https");
        }
        if (!domain.matches("^(?!-)[a-zA-Z0-9-]{1,63}(\\.[a-zA-Z0-9-]{1,63})+$")) {
            throw new IllegalArgumentException("Wrong domain (example: domain.ru)");
        }

        this.lifetimeHours = lifetimeHours;
        this.defaultMaxClicks = defaultMaxClicks;
        this.keyLength = keyLength;
        this.domain = domain;
        this.method = method;
    }

    public int getLifetimeHours() { return lifetimeHours; }

    public int getDefaultMaxClicks() { return defaultMaxClicks; }

    public int getKeyLength() { return keyLength; }

    public String getDomain() { return domain; }

    public String getMethod() { return method; }
}