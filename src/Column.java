import java.io.Serializable;

public class Column implements Serializable {
    public enum DataType {
        INT, DOUBLE, STRING, DATE, BOOLEAN
    }

    private final String name;
    private final DataType type;
    private final boolean isKey;      // Является ли первичным ключом
    private final boolean isRequired; // Обязательно ли для заполнения или можно поставить null

    public Column(String name, DataType type, boolean isKey, boolean isRequired) {
        this.name = name;
        this.type = type;
        this.isKey = isKey;
        this.isRequired = isRequired;
    }

    public String getName() {
        return name;
    }

    public DataType getType() {
        return type;
    }

    public boolean isKey() {
        return isKey;
    }

    public boolean isRequired() {
        return isRequired;
    }
}

