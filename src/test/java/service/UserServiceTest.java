package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import storage.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @TempDir Path temp;
    @Mock UserRepository repository;
    private Path file;
    private UserService service;

    @BeforeEach
    void setUp() {
        file = temp.resolve(".shortener/user.id");
        service = new UserService(repository, file);
    }

    @Test
    void persistsNewUuidAndReusesItOnNextLoad() throws IOException {
        String id = service.getUserId();
        assertEquals(UUID.fromString(id).toString(), id);
        assertEquals(id, Files.readString(file));
        when(repository.userExists(id)).thenReturn(true);
        assertEquals(id, new UserService(repository, file).getUserId());
        verify(repository, times(2)).userExists(id);
        verify(repository).createUser(id);
    }

    @Test
    void registersExistingLocalIdIfMissingFromRepository() throws IOException {
        String id = "01234567-89ab-cdef-0123-456789abcdef";
        write("  " + id + "\n");
        assertEquals(id, service.getUserId());
        var order = inOrder(repository);
        order.verify(repository).userExists(id);
        order.verify(repository).createUser(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-uuid", "1-1-1-1-1"})
    void rejectsCorruptIdWithoutAccessingRepository(String content) throws IOException {
        write(content);
        assertThrows(IOException.class, service::getUserId);
        verifyNoInteractions(repository);
    }

    private void write(String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
