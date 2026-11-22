package storage;

public interface UserRepository {
    public boolean userExists(String userId);

    public void createUser(String userId);
}
