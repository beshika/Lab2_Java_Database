import java.time.LocalDate;

public class Main {
    public static void main(String[] args) {
        Table warehouses = new Table("warehouses");
        warehouses.addColumn(new Column("id", Column.DataType.INT, true, true));
        warehouses.addColumn(new Column("address", Column.DataType.STRING, false, true));
        warehouses.addColumn(new Column("isFull", Column.DataType.BOOLEAN, false, false));

        DBrecord w1 = new DBrecord();
        w1.set("id", 1);
        w1.set("address", "ул. Большая Покровская, 10");
        w1.set("isFull", false);
        warehouses.addRecord(w1);
        DBrecord w2 = new DBrecord();
        w2.set("id", 2);
        w2.set("address", "пр. Гагарина, 25");
        w2.set("isFull", true);
        warehouses.addRecord(w2);

        Table products = new Table("products");
        products.addColumn(new Column("id", Column.DataType.INT, true, true));
        products.addColumn(new Column("name", Column.DataType.STRING, false, true));
        products.addColumn(new Column("price", Column.DataType.DOUBLE, false, true));
        products.addColumn(new Column("manufactureDate", Column.DataType.DATE, false, false));
        products.addColumn(new Column("quantity", Column.DataType.INT, false, true));
        products.addColumn(new Column("warehouseId", Column.DataType.INT, false, true));

        DBrecord p1 = new DBrecord();
        p1.set("id", 101);
        p1.set("name", "Митенки");
        p1.set("price", 750);
        p1.set("manufactureDate", LocalDate.of(2026, 5, 10));
        p1.set("quantity", 15);
        p1.set("warehouseId", 1);
        products.addRecord(p1);

        System.out.println("Таблица: " + warehouses.getTableName());
        for (DBrecord r : warehouses.getRecords()) {
            System.out.println(r);
        }

        System.out.println(products.getRecords());

        System.out.println(products.getKeyColumn().getName());
        System.out.println(p1.get("price"));
    }
}
