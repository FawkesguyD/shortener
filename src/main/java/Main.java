import controller.Controller;
import model.Config;
import service.ConfigService;
import service.LinkService;
import service.UserService;
import storage.RedisStorage;
import view.ConsoleView;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        ConfigService configService = new ConfigService();
        Config config = configService.load();

        RedisStorage storage = new RedisStorage();

        LinkService linkService = new LinkService(storage, config);
        UserService userService = new UserService(storage);


        String userId = userService.getUserId();


        Controller controller = new Controller(linkService, userService, userId);


        ConsoleView console = new ConsoleView(controller);
        console.start();
    }
}
