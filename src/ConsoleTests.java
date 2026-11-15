import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ConsoleTests {
    private static String testDB = "testdbconsole.dat";
    public static void main(String[] args) {
        System.out.println("Тестирование производительности БЕЗ индекса");
        int[] sizes = {10000, 50000, 100000};
        for (int size : sizes) {
            DBmanager testDb = createTestDatabase(size);
            testSelect(testDb, size, 100);
        }
        System.out.println("Тестирование производительности С индексом по столбцу price");
        for (int size : sizes) {
            DBmanager testDb = createTestDatabase(size);
            testDb.createIndex("idx_products_price", "products", "price");
            testSelect(testDb, size, 100);
        }
    }

    // Создает новую БД в памяти и заполняет таблицу products заданным числом записей
    private static DBmanager createTestDatabase(int rowCount) {
        DBmanager testDb = new DBmanager();
        List<DBrecord> pack = new ArrayList<>(rowCount);

        // Первые 100 продуктов с ценой 100, следующие 200 продуктов с ценой 200, следующие 300 с ценой 300 и тд
        double currentPrice = 100.0;
        int groupLimit = 100;   // Сколько товаров с текущей ценой (сначала 100)
        int countInGroup = 0;   // Счетчик товаров в текущей ценовой группе

        DBrecord warehouse = new DBrecord();
        warehouse.set("id", 1);
        warehouse.set("address", "Тестовый склад");
        warehouse.set("isFull", false);
        testDb.insert("warehouses", List.of(warehouse));

        for (int i = 1; i <= rowCount; i++) {
            DBrecord product = new DBrecord();
            product.set("id", i);
            product.set("name", "Товар_" + i);
            product.set("price", currentPrice);
            product.set("manufactureDate", LocalDate.of(2026, 1, 1));
            product.set("quantity", 50);
            product.set("warehouseId", 1);

            pack.add(product);
            countInGroup++;

            if (countInGroup == groupLimit) {
                currentPrice += 100.0;
                groupLimit += 100;
                countInGroup = 0;
            }
        }

        testDb.insert("products", pack);
        return testDb;
    }

     // Замеряет среднее время выполнения SELECT по цене
    private static void testSelect(DBmanager db, int rowCount, int iterations) {
        // Прогрев JVM как в JMH
        double targetPrice = 500.0;
        for (int i = 0; i < 1000; i++) {
            db.select("products", null, "price", "=", targetPrice);
        }

        double[] resultsMs = new double[5];
        // Основной замер
        for (int r = 0; r < 5; r++) {
            long startTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                db.select("products", null, "price", "=", targetPrice);
            }
            long endTime = System.nanoTime();

            double avgNanos = (double) (endTime - startTime) / iterations;
            resultsMs[r] = avgNanos / 1000000.0;
        }

        // Общее среднее по массиву результатов
        double sum = 0.0;
        for (double val : resultsMs) {
            sum += val;
        }
        double totalAverageMs = sum / 5;

        System.out.println("   ");
        System.out.println("Запрос: SELECT * FROM PRODUCTS WHERE price = " + targetPrice);
        System.out.println("Количество записей в таблице: " + rowCount);
        System.out.println("Количество прогонов запроса SELECT в одном замере: " + iterations);
        System.out.println("Замеры по сериям (мс): " + java.util.Arrays.toString(resultsMs));
        System.out.println("Итоговое среднее время (мс): " + totalAverageMs);
    }

}
