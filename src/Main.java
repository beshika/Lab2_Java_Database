import java.util.Scanner;

public class Main {
    private static String DB_FILE = "testdb.dat";
    public static void main(String[] args) {
        DBmanager db;
        try {
            // Загрузка БД
            db = DBmanager.loadFromFile(DB_FILE);
        } catch (IllegalStateException e) {
            System.out.println("Ошибка загрузки " + e.getMessage());
            db = new DBmanager();
        }

        DBparser parser = new DBparser(db);
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\nTestDB> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                // Сохранение при выходе
                db.saveToFile(DB_FILE);
                System.out.println("Данные сохранены. Выход.");
                break;
            }
            if (input.isEmpty()) {
                continue;
            }

            try {
                parser.execute(input);
                db.saveToFile(DB_FILE);
            }
            catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
            catch (Exception e) {
                System.out.println("Неожиданная ошибка: " + e.getMessage());
            }
        }
        scanner.close();
    }
}
