package controller;
import model.Link;
import service.LinkService;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class Controller {
    private final LinkService linkService;
    private final String userId;


    public Controller(LinkService linkService, String userId) {
        this.linkService = linkService;
        this.userId = userId;
    }

    public void deleteLink(String shortUrl) {
        linkService.deleteLink(userId, shortUrl);
    }

    public String createLink(String url, int clickMax) {
        return linkService.createLink(userId, url, clickMax);
    }

    public Link updateLink(String url, String newOriginalUrl) {
        return linkService.updateLink(userId, url, newOriginalUrl);
    }

    public Link getLink(String shortUrl) {
        return linkService.getLink(userId, shortUrl);
    }

    public List<Link> getUserLinks() {
        return linkService.getUserLinks(userId);
    }

    public void openLink(String shortUrl) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IllegalStateException("В этой среде нельзя открыть браузер");
        }

        String originalUrl = linkService.resolveLink(shortUrl);
        try {
            Desktop.getDesktop().browse(new URI(originalUrl));
        } catch (IOException | URISyntaxException | SecurityException e) {
            throw new IllegalStateException("Не удалось открыть ссылку в браузере", e);
        }
    }

    public String formatShortUrl(Link link) {
        return linkService.formatShortUrl(link);
    }

    public int getDefaultMaxClicks() {
        return linkService.getDefaultMaxClicks();
    }

    public String getUserId() {
        return userId;
    }

}
