import java.io.Serializable;
import java.util.*;

// Индекс по одному столбцу таблицы на основе TreeMap (красно-чёрного дерева)
public class DBindex implements Serializable {
    private final String indexName;
    private final String tableName;
    private final TreeMap<Object, List<DBrecord>> tree; // Ключ - значение любого поля ДБ; Значение - список записей БД, у которых это поле равно ключу

    public DBindex(String indexName, String tableName) {
        this.indexName = indexName;
        this.tableName = tableName;
        this.tree = new TreeMap<>();
    }

     // Добавление записи в индекс (null-значения в индекс не добавляются, чтобы не сломалось сравнение)
    public void add(Object key, DBrecord record) {
        if (key == null) {
            return;
        }
        List<DBrecord> list = tree.get(key);
        if (list == null) { // Если не нашлось - надо создать новую пару ключ-значение
            list = new ArrayList<>();
            tree.put(key, list);
        }
        list.add(record);
    }

     // Удаление конкретной записи из индекса
    public void remove(Object key, DBrecord record) {
        if (key == null) {
            return;
        }
        List<DBrecord> list = tree.get(key);
        if (list != null) {
            list.remove(record);
            if (list.isEmpty()) {
                tree.remove(key);
            }
        }
    }

    // Поиск записей по индексу
    public List<DBrecord> search(String operator, Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        // Точный поиск (=)
        if ("=".equals(operator)) {
            List<DBrecord> found = tree.get(value);
            if (found == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(found);
        }

        // Поиск по диапазонам (>, >=, <, <=)
        Map<Object, List<DBrecord>> subMap;
        switch (operator) {
            case ">":
                subMap = tree.tailMap(value, false);
                break;
            case ">=":
                subMap = tree.tailMap(value, true);
                break;
            case "<":
                subMap = tree.headMap(value, false);
                break;
            case "<=":
                subMap = tree.headMap(value, true);
                break;
            default: // Оператор != не поддерживается, тк нет выигрыша в скорости
                return null;
        }

        List<DBrecord> result = new ArrayList<>();
        for (List<DBrecord> recordsGroup : subMap.values()) {
            result.addAll(recordsGroup);
        }
        return result;

    }

    public String getIndexName() { return indexName; }
    public String getTableName() { return tableName; }
}

