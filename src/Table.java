import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Table implements Serializable {
    private final String tableName;
    private final List<Column> columns;
    private final List<DBrecord> DBrecords;

    public Table(String tableName) {
        this.tableName = tableName;
        this.columns = new ArrayList<>();
        this.DBrecords = new ArrayList<>();
    }

    public void addColumn(Column column) {
        columns.add(column);
    }
    // Возвращает столбец по названию
    public Column getColumn(String name) {
        for (Column column : columns) {
            if (column.getName().equalsIgnoreCase(name)) {
                return column;
            }
        }
        return null;
    }

    public String getTableName() {
        return tableName;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public List<DBrecord> getRecords() {
        return DBrecords;
    }
}
