package controller;
import model.Link;
import service.LinkService;

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

    public String resolveLink(String shortUrl) {
        return linkService.resolveLink(shortUrl);
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
