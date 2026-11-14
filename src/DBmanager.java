import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.io.*;

public class DBmanager implements Serializable{
    private Map<String, Table> tables; // Ключ - имя таблицы
    private Map<String, DBindex> indexes; // Ключ - имя_таблицы.имя_столбца

    public DBmanager() {
        this.tables = new HashMap<>();
        this.indexes = new HashMap<>();
        initializeTables();
    }

    private void initializeTables() {
        Table warehouses = new Table("warehouses");
        warehouses.addColumn(new Column("id", Column.DataType.INT, true, true));
        warehouses.addColumn(new Column("address", Column.DataType.STRING, false, true));
        warehouses.addColumn(new Column("isFull", Column.DataType.BOOLEAN, false, false));
        tables.put("warehouses", warehouses);

        Table products = new Table("products");
        products.addColumn(new Column("id", Column.DataType.INT, true, true));
        products.addColumn(new Column("name", Column.DataType.STRING, false, true));
        products.addColumn(new Column("price", Column.DataType.DOUBLE, false, true));
        products.addColumn(new Column("manufactureDate", Column.DataType.DATE, false, false));
        products.addColumn(new Column("quantity", Column.DataType.INT, false, true));
        products.addColumn(new Column("warehouseId", Column.DataType.INT, false, true));
        tables.put("products", products);
    }

     // Вставка одной записи в таблицу.
    public void insert(String tableName, List<DBrecord> records) {
        Table table = getTable(tableName);
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("Список записей для вставки пуст");
        }
        List<DBrecord> validatedRecords = new ArrayList<>();

        // Проверка существования переданных столбцов
        for (DBrecord record : records) {
            for (String fieldName : record.getFields().keySet()) {
                if (table.getColumn(fieldName) == null) {
                    throw new IllegalArgumentException(
                            "Столбец '" + fieldName + "' не существует в таблице '" + tableName + "'"
                    );
                }
            }

            DBrecord formalizedDBrecord = new DBrecord();
            // Проверка правильности типов, наличия обязательных полей для каждого столбца и уникальности первичного ключа
            for (Column col : table.getColumns()) {
                String colName = col.getName();
                Object value = getValueIgnoreCase(record, colName);
                if (value == null) {
                    if (col.isRequired()) {
                        throw new IllegalArgumentException(
                                "Обязательное поле '" + colName + "' не может быть пустым"
                        );
                    }
                    formalizedDBrecord.set(colName, null);
                    continue;
                }
                if (!isValidType(value, col.getType())) {
                    throw new IllegalArgumentException(
                            "Неверный тип данных для столбца '" + colName + "'. Ожидался: " + col.getType()
                    );
                }
                if (col.isKey()) {
                    checkPrimaryKeyIsUnique(table, colName, value);
                    if (!validatedRecords.isEmpty()){
                        for (DBrecord prev : validatedRecords) {
                            Object prevKey = prev.get(colName);
                            if (prevKey != null && prevKey.equals(value)) {
                                throw new IllegalArgumentException("Нарушение уникальности первичного ключа! В запросе дублируется " + colName + " = " + value);
                            }
                        }
                    }
                }
                formalizedDBrecord.set(colName, value);
            }
            validatedRecords.add(formalizedDBrecord);
        }
        table.getRecords().addAll(validatedRecords);
        // Обновление всех индексов с учетом добавленных записей
        for (Column col : table.getColumns()) {
            DBindex idx = indexes.get(makeIndexKey(table.getTableName(), col.getName()));
            if (idx != null) {
                for (DBrecord rec : validatedRecords) {
                    idx.add(rec.get(col.getName()), rec);
                }
            }
        }
    }

     // Если columnNames == null, выбираются все столбцы (*)
     // Если whereCol == null, выбираются все строки
    public List<DBrecord> select(String tableName, List<String> columnNames,
                               String whereCol, String whereOp, Object whereVal) {
        Table table = getTable(tableName);

        // Проверка запрашиваемых столбцов (если не *)
        List<Column> targetColumns = new ArrayList<>();
        if (columnNames == null) {
            targetColumns.addAll(table.getColumns());
        } else {
            for (String colName : columnNames) {
                Column col = table.getColumn(colName);
                if (col == null) {
                    throw new IllegalArgumentException(
                            "Столбец '" + colName + "' не найден в таблице '" + tableName + "'"
                    );
                }
                targetColumns.add(col);
            }
        }

        // Проверка условия WHERE на адекватность (правильный тип, оператор), если оно есть
        if (whereCol != null) {
            validateCondition(table, whereCol, whereOp, whereVal);
        }

        // Попытка достать значения БД по индексу
        List<DBrecord> candidateRecords = null;
        if (whereCol != null && whereVal != null) {
            Column condCol = table.getColumn(whereCol);
            DBindex idx = indexes.get(makeIndexKey(table.getTableName(), condCol.getName()));
            if (idx != null) {
                candidateRecords = idx.search(whereOp, whereVal); // Будут лежать все подходящие записи
            }
        }

        List<DBrecord> result = new ArrayList<>();
        if (candidateRecords != null) {
            for (DBrecord rec : candidateRecords) {
                DBrecord projected = new DBrecord();
                for (Column col : targetColumns) {
                    projected.set(col.getName(), rec.get(col.getName()));
                }
                result.add(projected);
            }
        }
        else {
            for (DBrecord original : table.getRecords()) {
                if (whereCol == null || matchesCondition(original, whereCol, whereOp, whereVal)) {
                    DBrecord projected = new DBrecord();
                    for (Column col : targetColumns) {
                        projected.set(col.getName(), original.get(col.getName()));
                    }
                    result.add(projected);
                }
            }
        }
        return result;
    }

    public int update(String tableName, Map<String, Object> newValues, String whereCol, String whereOp, Object whereVal) {
        Table table = getTable(tableName);

        if (newValues == null || newValues.isEmpty()) {
            throw new IllegalArgumentException("Не указаны поля для обновления");
        }

        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            String colName = entry.getKey();
            Object newVal = entry.getValue();

            Column col = table.getColumn(colName);
            if (col == null) {
                throw new IllegalArgumentException(
                        "Столбец '" + colName + "' не существует в таблице '" + tableName + "'"
                );
            }

            if (col.isKey()) {
                throw new IllegalArgumentException("Нельзя изменять первичный ключ '" + colName + "'");
            }

            if (newVal == null) {
                if (col.isRequired()) {
                    throw new IllegalArgumentException(
                            "Обязательное поле '" + colName + "' нельзя установить в null"
                    );
                }
            } else if (!isValidType(newVal, col.getType())) {
                throw new IllegalArgumentException(
                        "Неверный тип данных для столбца '" + colName + "'. Ожидался: " + col.getType()
                );
            }
        }
        // Проверка условия WHERE при наличии
        if (whereCol != null) {
            validateCondition(table, whereCol, whereOp, whereVal);
        }

        int updatedCount = 0;
        for (DBrecord recordToUpdate : table.getRecords()) {
            if (whereCol == null || matchesCondition(recordToUpdate, whereCol, whereOp, whereVal)) {
                for (Map.Entry<String, Object> entry : newValues.entrySet()) {
                    Column actualCol = table.getColumn(entry.getKey());
                    String colName = actualCol.getName();
                    Object oldVal = recordToUpdate.get(colName);
                    Object newVal = entry.getValue();

                    // Если по этому столбцу есть индекс, то записи в нем надо обновить
                    DBindex idx = indexes.get(makeIndexKey(table.getTableName(), colName));
                    if (idx != null) {
                        idx.remove(oldVal, recordToUpdate);
                        idx.add(newVal, recordToUpdate);
                    }
                    recordToUpdate.set(colName, newVal);
                }
                updatedCount++;
            }
        }
        return updatedCount;
    }

    public int delete(String tableName, String whereCol, String whereOp, Object whereVal) {
        Table table = getTable(tableName);

        if (whereCol != null) {
            validateCondition(table, whereCol, whereOp, whereVal);
        }

        int deletedCount = 0;
        Iterator<DBrecord> it = table.getRecords().iterator();
        while (it.hasNext()) {
            DBrecord recordTodelete = it.next();
            if (whereCol == null || matchesCondition(recordTodelete, whereCol, whereOp, whereVal)) {
                // Удаление записи из всех индексов таблицы
                for (Column col : table.getColumns()) {
                    DBindex idx = indexes.get(makeIndexKey(table.getTableName(), col.getName()));
                    if (idx != null) {
                        idx.remove(recordTodelete.get(col.getName()), recordTodelete);
                    }
                }
                it.remove(); // Удаление записи из БД
                deletedCount++;
            }
        }
        return deletedCount;
    }

     // Создаёт индекс по указанному столбцу таблицы и наполняет его текущими данными
    public void createIndex(String indexName, String tableName, String columnName) {
        Table table = getTable(tableName);
        Column col = table.getColumn(columnName);
        if (col == null) {
            throw new IllegalArgumentException(
                    "Столбец '" + columnName + "' не найден в таблице '" + tableName + "'"
            );
        }

        // Проверка на то, нет ли уже индекса на этом столбце или нет ли с таким же именем во всей таблице
        String key = makeIndexKey(table.getTableName(), col.getName());
        if (indexes.containsKey(key)) {
            throw new IllegalStateException(
                    "Индекс для столбца '" + col.getName() + "' в таблице '" + table.getTableName() + "' уже существует"
            );
        }
        for (DBindex existing : indexes.values()) {
            if (existing.getIndexName().equalsIgnoreCase(indexName)) {
                throw new IllegalStateException("Индекс с именем '" + indexName + "' уже существует");
            }
        }

        // Создание и заполнение индекса
        DBindex newIndex = new DBindex(indexName, table.getTableName(), col.getName());
        for (DBrecord record : table.getRecords()) {
            newIndex.add(record.get(col.getName()), record);
        }

        indexes.put(key, newIndex);
    }

    public Table getTable(String tableName) {
        Table table = tables.get(tableName.toLowerCase());
        if (table == null) {
            throw new IllegalArgumentException("Таблица '" + tableName + "' не найдена");
        }
        return table;
    }

    // Вспомогательные методы

    private Object getValueIgnoreCase(DBrecord DBrecord, String targetField) {
        for (Map.Entry<String, Object> entry : DBrecord.getFields().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(targetField)) {
                return entry.getValue();
            }
        }
        return null;
    }

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

    private boolean isValidType(Object value, Column.DataType expectedType) {
        switch (expectedType) {
            case INT: return value instanceof Integer;
            case DOUBLE: return value instanceof Double;
            case STRING: return value instanceof String;
            case DATE: return value instanceof LocalDate;
            case BOOLEAN: return value instanceof Boolean;
            default: return false;
        }
    }

    private void validateCondition(Table table, String whereCol, String whereOp, Object whereVal) {
        if (!whereOp.equals("=") && !whereOp.equals("!=") && !whereOp.equals(">")
                && !whereOp.equals("<") && !whereOp.equals(">=") && !whereOp.equals("<=")) {
            throw new IllegalArgumentException("Неизвестный оператор: " + whereOp);
        }
        Column col = table.getColumn(whereCol);
        if (col == null) {
            throw new IllegalArgumentException(
                    "Столбец '" + whereCol + "' в условии WHERE не найден в таблице '" + table.getTableName() + "'"
            );
        }
        if (whereVal != null && !isValidType(whereVal, col.getType())) {
            throw new IllegalArgumentException(
                    "Неверный тип значения в условии WHERE для столбца '" + col.getName() + "'. Ожидался: " + col.getType()
            );
        }
        if (col.getType() == Column.DataType.BOOLEAN
                && !whereOp.equals("=") && !whereOp.equals("!=")) {
            throw new IllegalArgumentException(
                    "Для BOOLEAN поддерживаются только операторы = и !="
            );
        }
    }

    private boolean matchesCondition(DBrecord record, String whereCol, String whereOp, Object whereVal) {
        Object actualValue = getValueIgnoreCase(record, whereCol);
        // Обработка NULL
        if (actualValue == null || whereVal == null) {
            if ("=".equals(whereOp)) {
                return actualValue == whereVal;
            }
            if ("!=".equals(whereOp)) {
                return actualValue != whereVal;
            }
            return false;
        }

        switch (whereOp) {
            case "=": return actualValue.equals(whereVal);
            case "!=": return !actualValue.equals(whereVal);
            case ">": return compareValues(actualValue, whereVal) > 0;
            case "<": return compareValues(actualValue, whereVal) < 0;
            case ">=": return compareValues(actualValue, whereVal) >= 0;
            case "<=": return compareValues(actualValue, whereVal) <= 0;
            default:
                throw new IllegalArgumentException("Неизвестный оператор сравнения: " + whereOp);
        }
    }

    private int compareValues(Object a, Object b) {
        if (a instanceof Integer) return Integer.compare((Integer) a, (Integer) b);
        if (a instanceof Double) return Double.compare((Double) a, (Double) b);
        if (a instanceof String) return ((String) a).compareTo((String) b);
        if (a instanceof LocalDate) return ((LocalDate) a).compareTo((LocalDate) b);
        throw new IllegalArgumentException("Для этого типа данных сравнение не поддерживается");
    }

    private String makeIndexKey(String tableName, String colName) {
        return tableName.toLowerCase() + "." + colName.toLowerCase();
    }
    
    // Работа с файлом
    
    public void saveToFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя файла для сохранения не может быть пустым");
        }
        try (FileOutputStream fos = new FileOutputStream(filename);
             ObjectOutputStream out = new ObjectOutputStream(fos)) {
            out.writeObject(this);
        }
        catch (FileNotFoundException ex) {
            throw new IllegalStateException("Не удалось создать или открыть файл для записи: '" + filename + "'. Проверьте путь и права доступа", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Ошибка ввода-вывода при сохранении базы данных в файл '" + filename + "': " + ex.getMessage(), ex);
        }
    }

    // Загрузка БД из файла; если файла ещё нет, создаётся новая пустая БД
    public static DBmanager loadFromFile(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя файла для загрузки не может быть пустым");
        }
        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("Файл базы данных не найден. Создана новая база данных с таблицами warehouses и products");
            return new DBmanager();
        }
        try (FileInputStream fis = new FileInputStream(filename);
             ObjectInputStream in = new ObjectInputStream(fis)) {
            return (DBmanager) in.readObject();
        }
        catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Ошибка десериализации: не найден класс при чтении файла '" + filename + "'", ex);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Файл базы данных '" + filename + "' повреждён или недоступен для чтения: " + ex.getMessage(), ex);
        }
    }
}


