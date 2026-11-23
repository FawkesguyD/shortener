package view;

import controller.Controller;
import model.Link;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ResourceLock(Resources.SYSTEM_OUT)
@ResourceLock("java.lang.System.in")
class ConsoleViewTest {
    private static final String KEY = "Ab1234";
    private static final String ORIGINAL = "https://example.com/";
    private static final String SHORT = "https://sho.rt/" + KEY;
    @Mock Controller controller;

    @Test
    void exitsOnCommandZero() {
        String output = run("0\n");
        assertTrue(output.contains("У вас пока нет ссылок"));
        assertTrue(output.contains("Goodbye!"));
    }

    @Test
    void reportsUnknownCommandAndRecovers() {
        String output = run("9\n\n0\n");
        assertTrue(output.contains("[ERROR] Неизвестная команда"));
        assertTrue(output.contains("Goodbye!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2\n", "9\n"})
    void exitsOnEofWithoutConfusingItWithExplicitExit(String script) {
        assertFalse(run(script).contains("Goodbye!"));
        verify(controller, never()).createLink(anyString(), anyInt());
    }

    @ParameterizedTest
    @CsvSource({"'', 0", "7, 7"})
    void createsLinkWithDefaultOrExplicitLimit(String input, int limit) {
        when(controller.getDefaultMaxClicks()).thenReturn(10);
        when(controller.createLink(ORIGINAL, limit)).thenReturn(SHORT);
        String output = run("2\n" + ORIGINAL + "\n" + input + "\n0\n");
        assertTrue(output.contains("Лимит переходов (Enter: 10)"));
        assertTrue(output.contains("[OK] Короткая ссылка: " + SHORT));
        verify(controller).createLink(ORIGINAL, limit);
    }

    @ParameterizedTest
    @CsvSource({"abc, Лимит переходов должен быть положительным целым числом",
            "-1, Лимит переходов должен быть больше нуля", "0, Лимит переходов должен быть больше нуля"})
    void rejectsInvalidLimitWithoutCreatingLink(String limit, String message) {
        assertTrue(run("2\n" + ORIGINAL + "\n" + limit + "\n\n0\n").contains("[ERROR] " + message));
        verify(controller, never()).createLink(anyString(), anyInt());
    }

    @Test
    void cancelsDeletionWithoutCallingController() {
        assertTrue(run("3\n" + KEY + "\nнет\n\n0\n").contains("[INFO] Удаление отменено"));
        verify(controller, never()).deleteLink(anyString());
    }

    @Test
    void confirmsDeletion() {
        assertTrue(run("3\n" + KEY + "\nДа\n0\n").contains("[OK] Ссылка удалена"));
        verify(controller).deleteLink(KEY);
    }

    @Test
    void changesOriginalUrl() {
        assertTrue(run("4\n" + KEY + "\n" + ORIGINAL + "\n0\n").contains("[OK] Ссылка обновлена"));
        verify(controller).updateLink(KEY, ORIGINAL);
    }

    @Test
    void canLeaveDetailsPromptWithoutFetchingLink() {
        Link link = new Link("owner", ORIGINAL, KEY, 0, 2, Instant.EPOCH, null);
        when(controller.getUserLinks()).thenReturn(List.of(link));
        assertTrue(run("5\n\n0\n").contains("Goodbye!"));
        verify(controller, never()).getLink(anyString());
    }

    @Test
    void showsDetailsWithClickCountAndStatus() {
        Link active = new Link("owner", ORIGINAL, KEY, 1, 2, Instant.EPOCH, null);
        Link exhausted = new Link("owner", ORIGINAL, "Cd5678", 2, 2, Instant.EPOCH, null);
        when(controller.getUserLinks()).thenReturn(List.of(active, exhausted));
        when(controller.getLink(KEY)).thenReturn(active);
        when(controller.formatShortUrl(active)).thenReturn(SHORT);
        String output = run("5\n" + KEY + "\n\n0\n");
        assertAll(() -> assertTrue(output.contains("Подробности ссылки")),
                () -> assertTrue(output.contains("Короткая ссылка: " + SHORT)),
                () -> assertTrue(output.contains("Исходный URL: " + ORIGINAL)),
                () -> assertTrue(output.contains("Переходы: 1/2")),
                () -> assertTrue(output.contains("без ограничения")),
                () -> assertTrue(output.contains("Статус: активна")),
                () -> assertTrue(output.contains("2/2 | неактивна")));
    }

    @Test
    void delegatesOpeningLinkWithoutLaunchingRealBrowser() {
        assertTrue(run("6\n" + KEY + "\n0\n").contains("[OK] Ссылка передана браузеру"));
        verify(controller).openLink(KEY);
    }

    @ParameterizedTest
    @MethodSource("commandErrors")
    void reportsCommandErrorAndReturnsToMenu(RuntimeException error, String message) {
        doThrow(error).when(controller).openLink(KEY);
        String output = run("6\n" + KEY + "\n\n0\n");
        assertTrue(output.contains("[ERROR] " + message));
        assertTrue(output.contains("Goodbye!"));
    }

    static Stream<Arguments> commandErrors() {
        return Stream.of(
                Arguments.of(new JedisConnectionException("offline"), "Не удалось подключиться к Redis"),
                Arguments.of(new JedisException("failed"), "Не удалось выполнить операцию в Redis"),
                Arguments.of(new IllegalArgumentException("bad URL"), "bad URL"),
                Arguments.of(new SecurityException("Access denied"), "Access denied"),
                Arguments.of(new IllegalStateException("Desktop unavailable"), "Desktop unavailable"));
    }

    @ParameterizedTest
    @MethodSource("listErrors")
    void canExitEvenWhenRedisListingFails(RuntimeException error, String message) {
        when(controller.getUserLinks()).thenThrow(error);
        String output = run("0\n");
        assertTrue(output.contains("[ERROR] " + message));
        assertTrue(output.contains("Goodbye!"));
    }

    static Stream<Arguments> listErrors() {
        return Stream.of(
                Arguments.of(new JedisConnectionException("offline"), "Не удалось подключиться к Redis: список ссылок недоступен"),
                Arguments.of(new JedisException("failed"), "Не удалось загрузить список ссылок из Redis"));
    }

    private String run(String script) {
        var originalIn = System.in;
        var originalOut = System.out;
        var output = new ByteArrayOutputStream();
        try (var capture = new PrintStream(output, true, UTF_8)) {
            System.setIn(new ByteArrayInputStream(script.getBytes(UTF_8)));
            System.setOut(capture);
            new ConsoleView(controller).start();
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
        return output.toString(UTF_8);
    }
}
