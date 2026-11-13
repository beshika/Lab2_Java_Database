import java.util.ArrayList;
import java.util.List;

public class TablePrinter {
    // records - список найденных записей
    // columns - список имен столбцов для отображения (может быть null, например при SELECT *)
    public void printTable(List<DBrecord> records, List<String> columns) {
        if (records == null || records.isEmpty()) {
            System.out.println("No rows found.");
            return;
        }

        // Если список столбцов не передан явно, они берутся из первой записи
        List<String> headers;
        if (columns != null && !columns.isEmpty()) {
            headers = columns;
        }
        else {
            DBrecord firstRecord = records.get(0);
            headers = new ArrayList<>(firstRecord.getFields().keySet());
        }

        // Вычисление ширины каждого столбца
        int[] widths = calculateWidths(records, headers);
        String separator = buildSeparator(widths);

        // Печать верхней границы
        System.out.println(separator);
        // Печать заголовков
        printRow(headers, widths);
        // Печать строки под шапкой
        System.out.println(separator);
        // Печать записей
        for (DBrecord record : records) {
            List<String> rowValues = new ArrayList<>();
            for (String colName : headers) {
                Object val = getValueIgnoreCase(record, colName);
                String strVal;
                if (val == null) {
                    strVal = "NULL";
                }
                else {
                    strVal = val.toString();
                }
                rowValues.add(strVal);

            }
            printRow(rowValues, widths);
        }

        // Печать нижней границы
        System.out.println(separator);
    }

     // Вычисляет максимальную ширину для каждого столбца среди заголовка и всех значений
    private int[] calculateWidths(List<DBrecord> records, List<String> headers) {
        int[] widths = new int[headers.size()];

        // Начальная ширина равна длине названия столбца
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }

        // Сравниваем с длиной каждого значения в столбце
        for (DBrecord record : records) {
            for (int i = 0; i < headers.size(); i++) {
                Object val = getValueIgnoreCase(record, headers.get(i));

                String strVal;
                if (val == null) {
                    strVal = "NULL";
                }
                else {
                    strVal = val.toString();
                }

                if (strVal.length() > widths[i]) {
                    widths[i] = strVal.length();
                }
            }
        }

        return widths;
    }

     // Формирует горизонтальную линию-разделитель вида +----+-------+
    private String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) {
            for (int i = 0; i < w + 2; i++) {
                sb.append("-");
            }
            sb.append("+");
        }
        return sb.toString();
    }

    // Печатает одну строку таблицы с выравниванием по левому краю
    private void printRow(List<String> values, int[] widths) {
        System.out.print("|");
        for (int i = 0; i < values.size(); i++) {
            System.out.printf(" %-" + widths[i] + "s |", values.get(i));
        }
        System.out.println();
    }

    private Object getValueIgnoreCase(DBrecord record, String colName) {
        for (String key : record.getFields().keySet()) {
            if (key.equalsIgnoreCase(colName)) {
                return record.get(key);
            }
        }
        return null;
    }
}
