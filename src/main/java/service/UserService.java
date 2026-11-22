package service;

import storage.UserRepository;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;


public class UserService {
    private final UserRepository repository;

    private static final Path USER_FILE = Path.of(System.getProperty("user.home"), ".shortener", "user.id");

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public String getUserId() throws IOException {

        String userId;
        if (Files.notExists(USER_FILE)) {
            userId = UUID.randomUUID().toString();
            Files.createDirectories(USER_FILE.getParent());
            Files.writeString(USER_FILE, userId, StandardOpenOption.CREATE_NEW);
        } else {
            userId = Files.readString(USER_FILE).trim();
            try {
                if (!UUID.fromString(userId).toString().equalsIgnoreCase(userId)) {
                    throw new IllegalArgumentException("Non-canonical UUID");
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid user ID in " + USER_FILE, e);
            }
        }
        checkUser(userId);
        return userId;
    }

    public void checkUser(String userId) {
        if (!repository.userExists(userId)) {
            repository.createUser(userId);
        }
    }
}
