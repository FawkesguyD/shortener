package service;

import model.Config;
import model.Link;
import storage.LinkRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class LinkService {
    private final LinkRepository repository;
    private final Config config;

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;

    public LinkService(LinkRepository repository, Config config) {
        this.repository = repository;
        this.config = config;
    }

    public Link updateLink(String userId, String shortUrl, String newOriginalUrl) {
        validateOriginalUrl(newOriginalUrl);

        String shortKey = extractKey(shortUrl);

        getLink(userId, shortKey);

        boolean updated = repository.updateLink(userId, shortKey, newOriginalUrl);

        if (!updated) {
            throw new IllegalStateException(
                    "Link was not updated"
            );
        }

        return getLink(userId, shortKey);
    }

    public void deleteLink(String userId, String shortUrl) {

        String shortKey = extractKey(shortUrl);

        getLink(userId, shortKey);

        boolean deleted = repository.deleteLink(userId, shortKey);

        if (!deleted) {
            throw new IllegalStateException(
                    "Link was not deleted"
            );
        }
    }

    public Link getLink(String userId, String shortUrl) {

        String shortKey = extractKey(shortUrl);

        Link link = repository.getLink(shortKey);

        if (link == null) {
            throw new IllegalArgumentException("Link not found");
        }

        if (!link.getOwnerId().equals(userId)) {
            throw new SecurityException("Access denied");
        }

        return link;
    }

    public List<Link> getUserLinks(String userId) {
        return repository.getUserLinks(userId);
    }

    public String resolveLink(String shortUrl) {
        String originalUrl = repository.resolveLink(extractKey(shortUrl));
        if (originalUrl == null) {
            throw new IllegalArgumentException("Ссылка не найдена, просрочена или исчерпала лимит переходов");
        }
        return originalUrl;
    }

    public String formatShortUrl(Link link) {
        return config.getBaseUrl() + link.getShortKey();
    }

    public int getDefaultMaxClicks() {
        return config.getDefaultMaxClicks();
    }

    public String createLink(String userId, String originalUrl, int clickMax) {

        validateOriginalUrl(originalUrl);

        int lifeTime = config.getLifetimeHours();
        int clicks = clickMax == 0 ? getDefaultMaxClicks() : clickMax;

        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(lifeTime, ChronoUnit.HOURS);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {

            String shortKey = generateKey();

            Link link = new Link(userId, originalUrl, shortKey, 0, clicks, createdAt, expiresAt);

            if (repository.saveLinkIfAbsent(link)) {
                return formatShortUrl(link);
            }
        }

        throw new IllegalStateException(
                "Failed to generate unique short link"
        );
    }

    private String generateKey() {
        int keyLength = config.getKeyLength();
        StringBuilder key = new StringBuilder(keyLength);

        for (int i = 0; i < keyLength; i++) {
            int index = RANDOM.nextInt(ALPHABET.length());
            key.append(ALPHABET.charAt(index));
        }

        return key.toString();
    }

    private void validateOriginalUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Original URL cannot be empty"
            );
        }
        URI uri = URI.create(originalUrl);
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) ||
                "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Original URL must be an absolute HTTP(S) URL");
        }
    }

    private String extractKey(String shortUrl) {

        if (shortUrl == null || shortUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Short URL cannot be empty"
            );
        }

        String value = shortUrl.trim();
        String shortKey;

        if (!value.contains("/") && !value.contains(":")) {
            shortKey = value;
        } else {
            try {
                String path = getPath(value);

                if (path == null ||
                        !path.startsWith("/") ||
                        path.lastIndexOf('/') != 0) {
                    throw new IllegalArgumentException(
                            "Invalid short URL"
                    );
                }

                shortKey = path.substring(1);

            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(
                        "Invalid short URL", e
                );
            }
        }
        if (shortKey.isEmpty() || !shortKey.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            throw new IllegalArgumentException("Invalid short key");
        }
        return shortKey;
    }

    private String getPath(String url) throws URISyntaxException {

        URI baseUri = URI.create(config.getBaseUrl());
        String normalized = url.contains("://") ? url : baseUri.getScheme() + "://" + url;

        URI uri = new URI(normalized);

        if (!baseUri.getScheme().equalsIgnoreCase(uri.getScheme()) ||
                !baseUri.getHost().equalsIgnoreCase(uri.getHost()) ||
                baseUri.getPort() != uri.getPort() ||
                uri.getRawUserInfo() != null ||
                uri.getRawQuery() != null ||
                uri.getRawFragment() != null ||
                !("https".equalsIgnoreCase(uri.getScheme()) ||
                        "http".equalsIgnoreCase(uri.getScheme()))) {

            throw new IllegalArgumentException(
                    "Invalid short URL"
            );
        }

        return uri.getRawPath();
    }
}
