package view;

import controller.Controller;

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

            String command = readInput();

            switch (command) {
                case "1" -> showLinks();
                case "2" -> createLink();
                case "3" -> deleteLink();
                case "4" -> changeLink();
                case "0" -> {
                    clearConsole();
                    System.out.println("URL SHORTENER / Goodbye!");
                    return;
                }
                default -> {
                    showError("Неизвестная команда");
                    waitForEnter();
                }
            }
        }
    }

    private void showLinks() {
        showScreen("Мои ссылки");

        controller.getUserLinks();

        waitForEnter();
    }

    private void createLink() {
        Scanner input = new Scanner(System.in);
        showScreen("Создание ссылки");

        System.out.println("Введите исходный URL");
        String originalUrl = readInput();

        System.out.println("Введите колличество переходов (default: 6)");
        String clickMax = readInput();
        if (clickMax.isEmpty()) {
            controller.createLink(originalUrl, 0);
        } else {
            controller.createLink(originalUrl, Integer.parseInt(clickMax));
        }

        waitForEnter();
    }

    private void deleteLink() {
        showScreen("Удаление ссылки");

        System.out.println("Введите короткий код ссылки");
        String shortUrl = readInput();

        controller.deleteLink(shortUrl);

        waitForEnter();
    }

    private void changeLink() {
        showScreen("Изменение ссылки");

        System.out.println("Введите короткий код ссылки");
        String shortUrl = readInput();
        System.out.println("Введите исходную ссылку, которую нужно сократить");
        String originalUrl = readInput();

        controller.updateLink(shortUrl, originalUrl);

        waitForEnter();
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

    private void waitForEnter() {
        System.out.println();
        System.out.println(LINE);
        System.out.print("Нажмите Enter для продолжения...");
        input.nextLine();
    }

    private void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
