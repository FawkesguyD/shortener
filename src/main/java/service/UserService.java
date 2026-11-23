package service;

import storage.UserRepository;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;


public class UserService {
    private final UserRepository repository;

    private final Path userFile;

    public UserService(UserRepository repository) {
        this(repository, Path.of(System.getProperty("user.home"), ".shortener", "user.id"));
    }

    UserService(UserRepository repository, Path userFile) {
        this.repository = repository;
        this.userFile = userFile.toAbsolutePath();
    }

    public String getUserId() throws IOException {

        String userId;
        if (Files.notExists(userFile)) {
            userId = UUID.randomUUID().toString();
            Files.createDirectories(userFile.getParent());
            Files.writeString(userFile, userId, StandardOpenOption.CREATE_NEW);
        } else {
            userId = Files.readString(userFile).trim();
            try {
                if (!UUID.fromString(userId).toString().equalsIgnoreCase(userId)) {
                    throw new IllegalArgumentException("Non-canonical UUID");
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid user ID in " + userFile, e);
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
