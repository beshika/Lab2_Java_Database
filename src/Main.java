import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        DBmanager db = new DBmanager();
        DBparser parser = new DBparser(db);
        String[] validQueries = {
                "INSERT INTO warehouses VALUES (1, 'ул. Большая Покровская, 10', FALSE);",
                "INSERT INTO warehouses (id, address) VALUES (2, 'пр. Гагарина, 25');",

                "INSERT INTO products VALUES (1, 'Митенки', 750.0, '2026-10-01', 15, 1);",
                "INSERT INTO products VALUES (2, 'Ноутбук', 75000.0, '2026-05-10', 5, 1), (3, 'Свитер', 7500.0, NULL, 10, 2);",
                "INSERT INTO products (id, name, price, quantity, warehouseId) VALUES (4, 'Монитор', 25000.0, 8, 2);",

                "SELECT * FROM products;",
                "SELECT name, price FROM products WHERE price > 20000;",

                "UPDATE products SET price = 30000.0 WHERE id = 2;",
                "SELECT * FROM products WHERE id = 2;",
                "UPDATE products SET quantity = 99;",
                "SELECT id, name, quantity FROM products;",

                "DELETE FROM products WHERE id = 2;",
                "SELECT * FROM products;",
                "DELETE FROM products;",
                "SELECT * FROM products;"
        };

        for (String query : validQueries) {
            runQuery(parser, query);
        }

        System.out.println("\n ----- Тест ошибок -------");

        // Подготовим одну запись для проверки UPDATE/DELETE с ошибками
        runQuery(parser, "INSERT INTO products VALUES (1, 'Тест', 100.0, NULL, 1, 1);");

        String[] invalidQueries = {"DELETE FROM", "DELETE products",
                "DELETE FROM products WHERE", "DELETE FROM products WHERE id",
                "UPDATE products", "UPDATE products SET",
                "UPDATE products SET price", "UPDATE products SET price 100",
                "UPDATE products SET price = 100.0 WHERE", "UPDATE products SET foobar = 100.0",
                "UPDATE products SET price = abc",
                "INSERT INTO products VALUES (1, 'a')",
                "INSERT INTO products VALUES (10, 'a', 1.0, NULL, 1, 1), (20)",
                "SELECT * FROM products WHERE price >", "SELECT * FROM products WHERE foolbar = 5",
                "SELECT * FROM products WHERE price ~ 100"
        };
        for (String query : invalidQueries) {
            runQuery(parser, query);
        }

        System.out.println("\nВведите запрос или 'exit' для выхода");
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\nMiniDB> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Завершение работы СУБД.");
                break;
            }
            if (input.isEmpty()) {
                continue;
            }
            runQuery(parser, input);
        }
        scanner.close();
    }

    private static void runQuery(DBparser parser, String query) {
        System.out.println(">> " + query);
        try {
            parser.execute(query);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println(" Ошибка: " + e.getMessage());
        } catch (Exception e) {
            System.out.println(" Неожиданная ошибка: " + e.getMessage());
        }
    }
}
