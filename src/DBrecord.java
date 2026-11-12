import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class DBrecord implements Serializable {

    private Map<String, Object> fields;
    public DBrecord() {
        this.fields = new LinkedHashMap<>();
    }

    public void set(String field, Object value) {
        fields.put(field, value);
    }

    public Object get(String field) {
        return fields.get(field);
    }

    public boolean containsField(String field) {
        return fields.containsKey(field);
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return fields.toString();
    }
}
