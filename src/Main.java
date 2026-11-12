import java.time.LocalDate;

public class Main {
    public static void main(String[] args) {
        DBmanager db = new DBmanager();
        try {
            DBrecord w1 = new DBrecord();
            w1.set("id", 1);
            w1.set("address", "ул. Большая Покровская, 10");
            db.insert("warehouses", w1);

            DBrecord p1 = new DBrecord();
            p1.set("id", 10);
            p1.set("name", "Митенки");
            p1.set("price", 750.0);
            p1.set("manufactureDate", LocalDate.of(2026, 10, 1));
            p1.set("quantity", 15);
            p1.set("warehouseId", 1);
            db.insert("products", p1);

            DBrecord p2 = new DBrecord();
            p2.set("id", 20);
            p2.set("name", "Свитер");
            p2.set("price", 7500.0);
            p2.set("manufactureDate", LocalDate.of(2026, 10, 1));
            p2.set("quantity", 10);
            db.insert("products", p2);

            System.out.println("Товары в базе: " + db.selectAll("products"));
            System.out.println("Склады в базе: " + db.selectAll("warehouses"));
            System.out.println("Цена и дата у товаров в базе: " + db.selectColumns("products", "price","manufactureDate"));

            db.insert("warehouses", w1);

        }
        catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
        catch (Exception e) {
            System.out.println("Неожиданная ошибка: " + e.getMessage());
        }
    }
}
