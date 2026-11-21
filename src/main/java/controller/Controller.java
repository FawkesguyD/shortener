package controller;
import model.Config;
import model.Link;
import service.ConfigService;
import service.LinkService;
import service.UserService;
import storage.LinkRepository;
import storage.RedisStorage;
import storage.UserRepository;

import java.io.IOException;

public class Controller {
    private final LinkService linkService;
    private final UserService userService;
    private final String userId;


    public Controller(LinkService linkService, UserService userService, String userId) {
        this.linkService = linkService;
        this.userService = userService;
        this.userId = userId;
    }

    public void deleteLink(String shortUrl) {
        linkService.deleteLink(userId, shortUrl);
    }

    public void createLink(String url, int clickMax) {
        linkService.createLink(userId, url, clickMax);
    }

    public void updateLink(String url, String newOriginalUrl) {
        linkService.updateLink(userId, url, newOriginalUrl);
    }

    public void getLink(String shortUrl) {
        linkService.getLink(userId, shortUrl);
    }

    public void getUserLinks() {
        linkService.getUserLinks(userId);
    }

    public String getUserId() {
        return userId;
    }

}
