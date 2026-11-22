package view;

import controller.Controller;
import model.Link;
import redis.clients.jedis.exceptions.JedisException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ConsoleView {

    private static final String LINE = "─────────────────────────────────────────────";
    private static final String POSTFIX = "  ❯ ";
    private final Scanner input = new Scanner(System.in);
    private final Controller controller;

    public ConsoleView(Controller controller) {
        this.controller = controller;
    }

    public void start() {
        while (true) {
            clearConsole();
            showMenu();

            try {
                switch (readInput()) {
                    case "1" -> showLinks();
                    case "2" -> createLink();
                    case "3" -> deleteLink();
                    case "4" -> changeLink();
                    case "5" -> showLinkDetails();
                    case "6" -> resolveLink();
                    case "0" -> {
                        clearConsole();
                        System.out.println("URL SHORTENER / Goodbye!");
                        return;
                    }
                    default -> showError("Неизвестная команда");
                }
            } catch (NoSuchElementException e) {
                return;
            } catch (JedisException e) {
                showError("Не удалось выполнить операцию в Redis");
            } catch (IllegalArgumentException | SecurityException | IllegalStateException e) {
                showError(e.getMessage());
            }
            if (!waitForEnter()) {
                return;
            }
        }
    }

    private void showLinks() {
        showScreen("Мои ссылки");

        List<Link> links = controller.getUserLinks();
        if (links.isEmpty()) {
            showInfo("У вас пока нет ссылок");
        } else {
            links.forEach(this::printLink);
        }
    }

    private void createLink() {
        showScreen("Создание ссылки");

        System.out.println("Введите исходный URL");
        String originalUrl = readInput();

        System.out.printf("Введите лимит переходов (Enter: %d)%n", controller.getDefaultMaxClicks());
        String value = readInput();
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

        showSuccess("Короткая ссылка: " + controller.createLink(originalUrl, clickMax));
    }

    private void deleteLink() {
        showScreen("Удаление ссылки");

        String shortUrl = readShortUrl();
        System.out.println("Удалить ссылку? [да/нет]");
        if (!"да".equalsIgnoreCase(readInput())) {
            showInfo("Удаление отменено");
            return;
        }

        controller.deleteLink(shortUrl);
        showSuccess("Ссылка удалена");
    }

    private void changeLink() {
        showScreen("Изменение ссылки");

        String shortUrl = readShortUrl();
        System.out.println("Введите новый исходный URL");
        String originalUrl = readInput();

        Link updated = controller.updateLink(shortUrl, originalUrl);
        showSuccess("Ссылка обновлена");
        printLink(updated);
    }

    private void showLinkDetails() {
        showScreen("Информация о ссылке");
        printLink(controller.getLink(readShortUrl()));
    }

    private void resolveLink() {
        showScreen("Переход по короткой ссылке");
        showInfo("Адрес назначения: " + controller.resolveLink(readShortUrl()));
    }

    private String readShortUrl() {
        System.out.println("Введите короткий URL или ключ ссылки");
        return readInput();
    }

    private void printLink(Link link) {
        System.out.println();
        System.out.println("Короткая ссылка: " + controller.formatShortUrl(link));
        System.out.println("Исходный URL: " + link.getOriginalUrl());
        System.out.printf("Переходы: %d/%d%n", link.getClickCount(), link.getClickMax());
        System.out.println("Создана: " + link.getCreatedAt());
        System.out.printf("Действует до: %s%n", link.getExpiresAt() == null ? "без ограничения" : link.getExpiresAt());
        System.out.println("Статус: " + (link.isExpired() ? "неактивна" : "активна"));
    }

    private void showScreen(String title) {
        clearConsole();
        showMenu();

        System.out.println();
        System.out.println("  ◆ " + title);
        System.out.println(LINE);
        System.out.println();
    }

    private void showMenu() {
        System.out.printf("◆ URL SHORTENER | %s\n", controller.getUserId());
        System.out.println(LINE);
        System.out.println("""
                
                  [1]  Просмотреть ссылки
                  [2]  Создать ссылку
                  [3]  Удалить ссылку
                  [4]  Изменить ссылку
                  [5]  Информация о ссылке
                  [6]  Получить адрес перехода
                
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


    private String readInput() {
        System.out.print(POSTFIX);
        return input.nextLine().trim();
    }

    private boolean waitForEnter() {
        System.out.println();
        System.out.println(LINE);
        System.out.print("Нажмите Enter для продолжения...");
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
