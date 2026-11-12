import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBmanager {
    private Map<String, Table> tables;

    public DBmanager() {
        this.tables = new HashMap<>();
        initializeTables();
    }
    // Создание таблиц предметной области
    private void initializeTables() {
        // Таблица складов (warehouses)
        Table warehouses = new Table("warehouses");
        warehouses.addColumn(new Column("id", Column.DataType.INT, true, true));
        warehouses.addColumn(new Column("address", Column.DataType.STRING, false, true));
        warehouses.addColumn(new Column("isFull", Column.DataType.BOOLEAN, false, false));
        tables.put("warehouses", warehouses);

        // Таблица товаров (products)
        Table products = new Table("products");
        products.addColumn(new Column("id", Column.DataType.INT, true, true));
        products.addColumn(new Column("name", Column.DataType.STRING, false, true));
        products.addColumn(new Column("price", Column.DataType.DOUBLE, false, true));
        products.addColumn(new Column("manufactureDate", Column.DataType.DATE, false, false));
        products.addColumn(new Column("quantity", Column.DataType.INT, false, true));
        products.addColumn(new Column("warehouseId", Column.DataType.INT, false, false));
        tables.put("products", products);
    }

    public void insert(String tableName, DBrecord givenDBrecord) {
        // Проверка, что таблица существует
        Table table = getExistingTable(tableName);

        // Проверка, что переданные столбцы существуют в таблице
        for (String fieldName : givenDBrecord.getFields().keySet()) {
            if (table.getColumn(fieldName) == null) {
                throw new IllegalArgumentException(
                        "Столбец '" + fieldName + "' не существует в таблице '" + tableName + "'"
                );
            }
        }

        // Создаем формализованную запись (чтобы заполнить пропущенные необязательные поля как null)
        DBrecord formalizedDBrecord = new DBrecord();

        for (Column col : table.getColumns()) {
            String colName = col.getName();
            Object value = getValueIgnoreCase(givenDBrecord, colName);

            // Проверка, что обязательные поля все присутствуют
            if (value == null) {
                if (col.isRequired()) {
                    throw new IllegalArgumentException(
                            "Обязательное поле '" + colName + "' не может быть пустым (null)"
                    );
                }
                formalizedDBrecord.set(colName, null);
                continue;
            }

            // Проверка соответствия типов данных
            if (!isValidType(value, col.getType())) {
                throw new IllegalArgumentException(
                        "Неверный тип данных для столбца '" + colName + "'. Ожидался: " + col.getType()
                );
            }

            // Проверка уникальности первичного ключа
            if (col.isKey()) {
                checkPrimaryKeyIsUnique(table, colName, value);
            }

            formalizedDBrecord.set(colName, value);
        }

        table.addRecord(formalizedDBrecord);
    }

    // Выбор как SELECT *
    public List<DBrecord> selectAll(String tableName) {
        Table table = getExistingTable(tableName);
        return new ArrayList<>(table.getRecords());
    }

    // Выбор конкретных столбцов из таблицы для всех строк
    public List<DBrecord> selectColumns(String tableName, String... columnNames) {
        Table table = getExistingTable(tableName);

        // Проверка, что все запрошенные столбцы существуют
        for (String colName : columnNames) {
            if (table.getColumn(colName) == null) {
                throw new IllegalArgumentException(
                        "Столбец '" + colName + "' не найден в таблице '" + tableName + "'"
                );
            }
        }

        List<DBrecord> result = new ArrayList<>();
        for (DBrecord original : table.getRecords()) {
            DBrecord selectedDBrecord = new DBrecord();
            for (String colName : columnNames) { // По каждому из введенных столбцов
                Column actualCol = table.getColumn(colName); // Возвращает столбец по названию
                selectedDBrecord.set(actualCol.getName(), original.get(actualCol.getName())); // Строим запись только из нужных столбцов
            }
            result.add(selectedDBrecord);
        }
        return result;
    }

    // ___Вспомогательные методы___

    private Table getExistingTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Имя таблицы не передано");
        }
        Table table = tables.get(tableName.toLowerCase());
        if (table == null) {
            throw new IllegalArgumentException("Таблица '" + tableName + "' не найдена");
        }
        return table;
    }

     // Достает значение из DBrecord без учета регистра имени поля
    private Object getValueIgnoreCase(DBrecord DBrecord, String targetField) {
        for (Map.Entry<String, Object> entry : DBrecord.getFields().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(targetField)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // Проверка на то, что в столбец вставляются данные ожидаемого для него типа
    private boolean isValidType(Object value, Column.DataType expectedType) {
        switch (expectedType) {
            case INT:
                return value instanceof Integer;
            case DOUBLE:
                return value instanceof Double;
            case STRING:
                return value instanceof String;
            case DATE:
                return value instanceof LocalDate;
            case BOOLEAN:
                return value instanceof Boolean;
            default:
                return false;
        }
    }

    // Проверка на уникальность первичного ключа
    private void checkPrimaryKeyIsUnique(Table table, String keyColName, Object newKeyValue) {
        for (DBrecord existing : table.getRecords()) {
            Object existingKey = existing.get(keyColName);
            if (existingKey != null && existingKey.equals(newKeyValue)) {
                throw new IllegalArgumentException(
                        "Нарушение уникальности первичного ключа! Запись с " + keyColName + " = " + newKeyValue + " уже существует."
                );
            }
        }
    }


}

