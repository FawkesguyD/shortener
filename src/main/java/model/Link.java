package model;
import java.time.Instant;

public class Link {
    private String ownerId;
    private String originalUrl;
    private String shortKey;
    private int clickCount;
    private int clickMax;
    private final Instant createdAt;
    private Instant expiresAt;

    public Link(String ownerId, String originalUrl, String shortKey, int clickCount, int clickMax, Instant createdAt, Instant expiresAt) {
        if (clickMax <= 0) {
            throw new IllegalArgumentException("clickMax must be > 0");
        }
        if (clickCount < 0) {
            throw new IllegalArgumentException("clickCount must be > 0");
        }

        if (expiresAt != null && expiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("expiresAt cannot be before createdAt");
        }

        this.ownerId        = ownerId;
        this.originalUrl    = originalUrl;
        this.shortKey       = shortKey;
        this.clickCount     = clickCount;
        this.clickMax       = clickMax;
        this.createdAt      = createdAt;
        this.expiresAt      = expiresAt;
    }

    public boolean isExpired() {
        boolean clicksExceeded = clickCount >= clickMax;

        boolean timeExpired = expiresAt != null
                && !Instant.now().isBefore(expiresAt);

        return clicksExceeded || timeExpired;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getShortKey() {
        return shortKey;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public int getClickCount() {
        return clickCount;
    }

    public int getClickMax() {
        return clickMax;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
