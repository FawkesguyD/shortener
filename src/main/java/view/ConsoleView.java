package view;

import controller.Controller;
import model.Link;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ConsoleView {

    private static final String LINE = "─────────────────────────────────────────────";
    private final Scanner input = new Scanner(System.in);
    private final Controller controller;

    public ConsoleView(Controller controller) {
        this.controller = controller;
    }

    public void start() {
        String success = null;
        while (true) {
            showHome(success);
            success = null;

            try {
                switch (readInput("Команда")) {
                    case "2" -> success = createLink();
                    case "3" -> success = deleteLink();
                    case "4" -> success = changeLink();
                    case "5" -> {
                        if (!showLinkDetails("Информация о ссылке")) {
                            continue;
                        }
                    }
                    case "6" -> success = openLink();
                    case "0" -> {
                        clearConsole();
                        System.out.println("URL SHORTENER / Goodbye!");
                        return;
                    }
                    default -> showError("Неизвестная команда");
                }
            } catch (NoSuchElementException e) {
                return;
            } catch (JedisConnectionException e) {
                showError("Не удалось подключиться к Redis");
            } catch (JedisException e) {
                showError("Не удалось выполнить операцию в Redis");
            } catch (IllegalArgumentException | SecurityException | IllegalStateException e) {
                showError(e.getMessage());
            }
            if (success == null && !waitForEnter()) {
                return;
            }
        }
    }

    private boolean showUserLinks() {
        System.out.println("Мои ссылки");
        try {
            List<Link> links = controller.getUserLinks();
            if (links.isEmpty()) {
                System.out.println("  У вас пока нет ссылок");
            } else {
                for (Link link : links) {
                    System.out.printf("  [%s] %s → %s | %d/%d | %s%n",
                            link.getShortKey(), controller.formatShortUrl(link), link.getOriginalUrl(),
                            link.getClickCount(), link.getClickMax(), linkStatus(link));
                }
            }
            return !links.isEmpty();
        } catch (JedisConnectionException e) {
            showError("Не удалось подключиться к Redis: список ссылок недоступен");
        } catch (JedisException e) {
            showError("Не удалось загрузить список ссылок из Redis");
        } finally {
            System.out.println(LINE);
        }
        return false;
    }

    private String createLink() {
        showScreen("Создание ссылки");

        String originalUrl = readInput("Исходный URL");

        String value = readInput("Лимит переходов (Enter: " + controller.getDefaultMaxClicks() + ")");
        int clickMax = 0;
        if (!value.isEmpty()) {
            try {
                clickMax = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Лимит переходов должен быть положительным целым числом");
            }
            if (clickMax <= 0) {
                throw new IllegalArgumentException("Лимит переходов должен быть больше нуля");
            }
        }

        return "Короткая ссылка: " + controller.createLink(originalUrl, clickMax);
    }

    private String deleteLink() {
        showScreen("Удаление ссылки");

        String shortUrl = readShortUrl();
        if (!"да".equalsIgnoreCase(readInput("Удалить ссылку? [да/нет]"))) {
            showInfo("Удаление отменено");
            return null;
        }

        controller.deleteLink(shortUrl);
        return "Ссылка удалена";
    }

    private String changeLink() {
        showScreen("Изменение ссылки");

        String shortUrl = readShortUrl();
        String originalUrl = readInput("Новый исходный URL");

        controller.updateLink(shortUrl, originalUrl);
        return "Ссылка обновлена";
    }

    private boolean showLinkDetails(String title) {
        if (!showScreen(title)) {
            return true;
        }
        String shortUrl = readInput("Ключ или короткий URL для подробностей (Enter: в меню)");
        if (shortUrl.isEmpty()) {
            return false;
        }
        System.out.println();
        System.out.println("Подробности ссылки");
        System.out.println(LINE);
        printLink(controller.getLink(shortUrl));
        return true;
    }

    private String openLink() {
        showScreen("Переход по короткой ссылке");
        controller.openLink(readShortUrl());
        return "Ссылка передана браузеру";
    }

    private String readShortUrl() {
        return readInput("Ключ или короткий URL");
    }

    private void printLink(Link link) {
        System.out.println("Короткая ссылка: " + controller.formatShortUrl(link));
        System.out.println("Исходный URL: " + link.getOriginalUrl());
        System.out.printf("Переходы: %d/%d%n", link.getClickCount(), link.getClickMax());
        System.out.println("Создана: " + link.getCreatedAt());
        System.out.printf("Действует до: %s%n", link.getExpiresAt() == null ? "без ограничения" : link.getExpiresAt());
        System.out.println("Статус: " + linkStatus(link));
    }

    private String linkStatus(Link link) {
        return link.isExpired() ? "неактивна" : "активна";
    }

    private void showHome(String success) {
        clearConsole();
        showMenu();
        showUserLinks();
        if (success != null) {
            showSuccess(success);
            System.out.println(LINE);
        }
    }

    private boolean showScreen(String title) {
        clearConsole();
        System.out.println("  ◆ " + title);
        System.out.println(LINE);
        return showUserLinks();
    }

    private void showMenu() {
        System.out.printf("◆ URL SHORTENER | %s%n", controller.getUserId());
        System.out.println(LINE);
        System.out.println("""
                Команды
                  [2]  Создать ссылку
                  [3]  Удалить ссылку
                  [4]  Изменить ссылку
                  [5]  Информация о ссылке
                  [6]  Открыть короткую ссылку в браузере

                  [0]  Выход

                """);
        System.out.println(LINE);
    }

    public void showSuccess(String message) {
        System.out.println();
        System.out.println("[OK] " + message);
    }

    public void showError(String message) {
        System.out.println();
        System.out.println("[ERROR] " + message);
    }

    public void showInfo(String message) {
        System.out.println();
        System.out.println("[INFO] " + message);
    }

    private String readInput(String label) {
        System.out.print(label + " ❯ ");
        System.out.flush();
        return input.nextLine().trim();
    }

    private boolean waitForEnter() {
        System.out.println();
        System.out.println(LINE);
        System.out.print("Enter: вернуться в меню ");
        System.out.flush();
        if (!input.hasNextLine()) {
            return false;
        }
        input.nextLine();
        return true;
    }

    private void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
