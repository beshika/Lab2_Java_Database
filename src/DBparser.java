import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DBparser {
    private final DBmanager dbManager;

    public DBparser(DBmanager dbManager) {
        this.dbManager = dbManager;
    }

    public List<DBrecord> execute(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        List<String> tokens = breakIntoTokens(query);
        if (tokens.isEmpty()) {
            return null;
        }
        String command = tokens.get(0).toUpperCase();
        switch (command) {
            case "INSERT":
                parseInsert(tokens);
                return null;
            case "SELECT":
                return parseSelect(tokens);
            case "UPDATE":
                parseUpdate(tokens);
                return null;
            case "DELETE":
                parseDelete(tokens);
                return null;
            case "CREATE":
                parseCreate(tokens);
                return null;
            case "DROP":
                parseDrop(tokens);
                return null;
            default:
                throw new IllegalArgumentException("Неизвестная команда: " + tokens.get(0));
        }
    }

    private void parseInsert(List<String> tokens) {
        if (!tokenAt(tokens, 1).equalsIgnoreCase("INTO")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: после INSERT ожидалось INTO");
        }
        String tableName = tokenAt(tokens, 2);
        Table table = dbManager.getTable(tableName);
        int pos = 3;
        List<Column> targetColumns = new ArrayList<>();
        // Проверка на то, указаны ли конкретные столбцы в скобках (как в примере INSERT INTO cars (id, brand) VALUES)
        if (tokenAt(tokens, pos).equals("(")) {
            pos++;
            while (!tokenAt(tokens, pos).equals(")")) {
                String colName = tokenAt(tokens, pos);
                Column col = table.getColumn(colName);
                if (col == null) {
                    throw new IllegalArgumentException("Столбец '" + colName + "' не найден в таблице '" + tableName + "'");
                }
                targetColumns.add(col);
                pos++;
                if (tokenAt(tokens, pos).equals(",")) {
                    pos++;
                }
                else if (!tokenAt(tokens, pos).equals(")")) {
                    throw new IllegalArgumentException("Ожидалась запятая или ')' в списке столбцов");
                }
            }
            pos++;
        }
        else {
            // Если столбцы не указаны, берем все столбцы таблицы по порядку
            targetColumns.addAll(table.getColumns());
        }
        if (!tokenAt(tokens, pos).equalsIgnoreCase("VALUES")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: ожидалось ключевое слово VALUES");
        }
        pos++;

        List<DBrecord> recordsToInsert = new ArrayList<>();
        while (true) {
            if (!tokenAt(tokens, pos).equals("(")) {
                throw new IllegalArgumentException("Ошибка в синтаксисе: ожидалась '(' перед списком значений");
            }
            pos++;
            // В rawValues кладутся строки с передаваемыми значениями
            List<String> rawValues = new ArrayList<>();
            while (!tokenAt(tokens, pos).equals(")")) {
                rawValues.add(tokenAt(tokens, pos));
                pos++;
                if (tokenAt(tokens, pos).equals(",")) {
                    pos++;
                }
                else if (!tokenAt(tokens, pos).equals(")")) {
                    throw new IllegalArgumentException("Ожидалась запятая или ')' в списке значений");
                }
            }
            pos++;

            if (rawValues.size() != targetColumns.size()) {
                throw new IllegalArgumentException("Количество столбцов (" + targetColumns.size() + ") не совпадает с количеством значений (" + rawValues.size() + ")");
            }
            // Формирование DBrecord происходит при сопоставлении значения с выбранными столбцами
            DBrecord record = new DBrecord();
            for (int i = 0; i < targetColumns.size(); i++) {
                Column col = targetColumns.get(i);
                Object typedValue = convertValue(rawValues.get(i), col.getType());
                record.set(col.getName(), typedValue);
            }
            recordsToInsert.add(record);
            if (pos < tokens.size() && tokenAt(tokens, pos).equals(",")) {
                pos++;
            }
            else {
                break;
            }
        }
        if (pos != tokens.size()) {
            throw new IllegalArgumentException("Лишние токены в конце запроса INSERT");
        }
        dbManager.insert(tableName, recordsToInsert);
        System.out.println("В таблицу " + tableName + " успешно добавлено записей: " + recordsToInsert.size());
    }

    private List<DBrecord> parseSelect(List<String> tokens) {
        int pos = 1;
        List<String> selectedColumns = null; // Если список null, значит передали Select *
        if (tokenAt(tokens, pos).equals("*")) {
            pos++;
        }
        else {
            // Или собираем все выбранные столбцы
            selectedColumns = new ArrayList<>();
            while (true) {
                selectedColumns.add(tokenAt(tokens, pos));
                pos++;
                if (tokenAt(tokens, pos).equals(",")) {
                    pos++;
                }
                else {
                    break;
                }
            }
        }
        if (!tokenAt(tokens, pos).equalsIgnoreCase("FROM")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: ожидалось ключевое слово FROM");
        }
        pos++;

        String tableName = tokenAt(tokens, pos);
        Table table = dbManager.getTable(tableName);
        pos++;
        String whereCol = null;
        String whereOp = null;
        Object whereVal = null;
        // Проверка на наличие условия WHERE
        if (pos < tokens.size()) {
            if (!tokenAt(tokens, pos).equalsIgnoreCase("WHERE")) {
                throw new IllegalArgumentException("Ожидалось WHERE, но найдено: " + tokenAt(tokens, pos));
            }
            whereCol = tokenAt(tokens, pos + 1);
            whereOp = tokenAt(tokens, pos + 2);
            String rawWhereVal = tokenAt(tokens, pos + 3);
            if (pos + 4 != tokens.size()) {
                throw new IllegalArgumentException("Лишние токены после условия WHERE");
            }
            Column condCol = table.getColumn(whereCol);
            if (condCol == null) {
                throw new IllegalArgumentException("Столбец '" + whereCol + "' не найден в таблице '" + tableName + "'");
            }
            whereVal = convertValue(rawWhereVal, condCol.getType());
        }

        List<DBrecord> rows = dbManager.select(tableName, selectedColumns, whereCol, whereOp, whereVal);
        return rows;
    }

    private void parseUpdate(List<String> tokens) {
        String tableName = tokenAt(tokens, 1);
        Table table = dbManager.getTable(tableName);

        if (!tokenAt(tokens, 2).equalsIgnoreCase("SET")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: после имени таблицы ожидалось SET");
        }
        int pos = 3;
        Map<String, Object> newValues = new LinkedHashMap<>();
        // Чтение пар вида ключ(имя столбца) = значение в столбце
        while (true) {
            String colName = tokenAt(tokens, pos);
            String eq = tokenAt(tokens, pos + 1);
            if (!eq.equals("=")) {
                throw new IllegalArgumentException("В блоке SET ожидался знак '=', но найдено: " + eq);
            }
            String givenVal = tokenAt(tokens, pos + 2);

            Column col = table.getColumn(colName);
            if (col == null) {
                throw new IllegalArgumentException("Столбец '" + colName + "' не найден в таблице '" + tableName + "'");
            }
            newValues.put(col.getName(), convertValue(givenVal, col.getType()));
            pos += 3;

            if (pos < tokens.size() && tokenAt(tokens, pos).equals(",")) {
                pos++;
            }
            else {
                break;
            }
        }

        String whereCol = null;
        String whereOp = null;
        Object whereVal = null;
        if (pos < tokens.size()) {
            if (!tokenAt(tokens, pos).equalsIgnoreCase("WHERE")) {
                throw new IllegalArgumentException("Ожидалось WHERE, но найдено: " + tokenAt(tokens, pos));
            }
            whereCol = tokenAt(tokens, pos + 1);
            whereOp = tokenAt(tokens, pos + 2);
            String givenWhereVal = tokenAt(tokens, pos + 3);

            if (pos + 4 != tokens.size()) {
                throw new IllegalArgumentException("Лишние токены после условия WHERE");
            }
            Column condCol = table.getColumn(whereCol);
            if (condCol == null) {
                throw new IllegalArgumentException("Столбец '" + whereCol + "' не найден в таблице '" + tableName + "'");
            }
            whereVal = convertValue(givenWhereVal, condCol.getType());
        }
        int updated = dbManager.update(tableName, newValues, whereCol, whereOp, whereVal);
        System.out.println("Обновлено записей в таблице " + tableName + ": " + updated);
    }

    private void parseDelete(List<String> tokens) {
        if (!tokenAt(tokens, 1).equalsIgnoreCase("FROM")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: после DELETE ожидалось FROM");
        }
        String tableName = tokenAt(tokens, 2);
        Table table = dbManager.getTable(tableName);
        String whereCol = null;
        String whereOp = null;
        Object whereVal = null;
        if (tokens.size() > 3) {
            if (!tokenAt(tokens, 3).equalsIgnoreCase("WHERE")) {
                throw new IllegalArgumentException("Ожидалось WHERE, но найдено: " + tokenAt(tokens, 3));
            }
            whereCol = tokenAt(tokens, 4);
            whereOp = tokenAt(tokens, 5);
            String rawWhereVal = tokenAt(tokens, 6);
            if (tokens.size() > 7) {
                throw new IllegalArgumentException("Лишние токены после условия WHERE");
            }
            Column condCol = table.getColumn(whereCol);
            if (condCol == null) {
                throw new IllegalArgumentException("Столбец '" + whereCol + "' не найден в таблице '" + tableName + "'");
            }
            whereVal = convertValue(rawWhereVal, condCol.getType());
        }
        int deleted = dbManager.delete(tableName, whereCol, whereOp, whereVal);
        System.out.println("Удалено записей в таблице " + tableName+": " + deleted);
    }

    private void parseCreate(List<String> tokens) {
        String subCommand = tokenAt(tokens, 1);
        if (subCommand.equalsIgnoreCase("INDEX")) {
            parseCreateIndex(tokens);
        }
        else if (subCommand.equalsIgnoreCase("TABLE")) {
            parseCreateTable(tokens);
        }
        else {
            throw new IllegalArgumentException("После CREATE ожидалось TABLE или INDEX");
        }
    }

    private void parseCreateTable(List<String> tokens) {
        String tableName = tokenAt(tokens, 2);
        if (!tokenAt(tokens, 3).equals("(")) {
            throw new IllegalArgumentException("После имени таблицы ожидалась '('");
        }
        int pos = 4;
        List<Column> columns = new ArrayList<>();
        while (!tokenAt(tokens, pos).equals(")")) {
            String colName = tokenAt(tokens, pos);
            String typeStr = tokenAt(tokens, pos + 1).toUpperCase();
            Column.DataType dataType = parseColumnType(typeStr);
            pos += 2;
            boolean isKey = false;
            boolean isRequired = false;

            // Проверка на флаги PRIMARY KEY и NOT NULL
            while (pos < tokens.size() && !tokenAt(tokens, pos).equals(",")
                    && !tokenAt(tokens, pos).equals(")")) {
                String flag = tokenAt(tokens, pos);
                if (flag.equalsIgnoreCase("PRIMARY") && tokenAt(tokens, pos + 1).equalsIgnoreCase("KEY")) {
                    isKey = true;
                    isRequired = true;
                    pos += 2;
                }
                else if (flag.equalsIgnoreCase("NOT") && tokenAt(tokens, pos + 1).equalsIgnoreCase("NULL")) {
                    isRequired = true;
                    pos += 2;
                }
                else {
                    throw new IllegalArgumentException("Неизвестный модификатор столбца: " + tokenAt(tokens, pos));
                }
            }
            columns.add(new Column(colName, dataType, isKey, isRequired));

            if (tokenAt(tokens, pos).equals(",")) {
                pos++;
            }
            else if (!tokenAt(tokens, pos).equals(")")) {
                throw new IllegalArgumentException("Ожидалась запятая или ')' в описании столбцов");
            }
        }
        if (pos != tokens.size() - 1) {
            throw new IllegalArgumentException("Лишние токены в конце команды CREATE TABLE");
        }
        dbManager.createTable(tableName, columns);
        System.out.println("Таблица '" + tableName + "' успешно создана");
    }

    private Column.DataType parseColumnType(String typeStr) {
        switch (typeStr) {
            case "INT":
            case "INTEGER":
                return Column.DataType.INT;
            case "DOUBLE":
            case "FLOAT":
                return Column.DataType.DOUBLE;
            case "STRING":
            case "VARCHAR":
                return Column.DataType.STRING;
            case "DATE":
                return Column.DataType.DATE;
            case "BOOL":
            case "BOOLEAN":
                return Column.DataType.BOOLEAN;
            default:
                throw new IllegalArgumentException("Неподдерживаемый тип данных: " + typeStr);
        }
    }

    private void parseDrop(List<String> tokens) {
        if (!tokenAt(tokens, 1).equalsIgnoreCase("TABLE")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: после DROP ожидалось TABLE");
        }
        String tableName = tokenAt(tokens, 2);
        if (tokens.size() > 3) {
            throw new IllegalArgumentException("Лишние токены в конце команды DROP TABLE");
        }
        dbManager.dropTable(tableName);
        System.out.println("Таблица '" + tableName + "' успешно удалена");
    }

    private void parseCreateIndex(List<String> tokens) {
        if (!tokenAt(tokens, 1).equalsIgnoreCase("INDEX")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: после CREATE ожидалось INDEX");
        }
        String indexName = tokenAt(tokens, 2);
        if (!tokenAt(tokens, 3).equalsIgnoreCase("ON")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: после имени индекса ожидалось ON");
        }
        String tableName = tokenAt(tokens, 4);
        if (!tokenAt(tokens, 5).equals("(")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: ожидалась '(' перед именем столбца");
        }
        String columnName = tokenAt(tokens, 6);
        if (!tokenAt(tokens, 7).equals(")")) {
            throw new IllegalArgumentException("Ошибка в синтаксисе: ожидалась ')' после имени столбца");
        }
        if (tokens.size() > 8) {
            throw new IllegalArgumentException("Лишние токены в конце команды CREATE INDEX");
        }
        dbManager.createIndex(indexName, tableName, columnName);
        System.out.println("Индекс '" + indexName + "' успешно создан для " + tableName + "(" + columnName + ")");
    }

    // Вспомогательные методы

    // Конвертация объекта к типу данных, который DBmanager может обработать
    private Object convertValue(String raw, Column.DataType type) {
        if (raw.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            switch (type) {
                case INT:
                    return Integer.parseInt(raw);
                case DOUBLE:
                    return Double.parseDouble(raw);
                case STRING:
                    return raw;
                case DATE:
                    return LocalDate.parse(raw); // Формат YYYY-MM-DD
                case BOOLEAN:
                    if (raw.equalsIgnoreCase("true")) return true;
                    if (raw.equalsIgnoreCase("false")) return false;
                    throw new IllegalArgumentException("Ожидалось TRUE или FALSE, получено: " + raw);
                default:
                    throw new IllegalArgumentException("Неизвестный тип данных");
            }
        }
        catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException("Не удалось преобразовать значение '" + raw + "' к типу " + type);
        }
    }

    // Разбиение строки на части
    // Например, запрос SELECT name, price FROM products WHERE price >= 500.0
    // разобьется на ["SELECT", "name", ",", "price", "FROM", "products", "WHERE", "price", ">=", "500.0"]
    private List<String> breakIntoTokens(String src) {
        List<String> tokens = new ArrayList<>();
        int pos = 0;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            // Пропуск пробелов и точки с запятой
            if (isSpace(c) || c == ';') {
                pos++;
            }
            // Скобки и запятую нужно сохранить для INSERT
            else if (c == '(' || c == ')' || c == ',') {
                tokens.add("" + c);
                pos++;
            }
            // Фраза в одинарных кавычках
            else if (c == '\'') {
                int end = src.indexOf('\'', pos + 1);
                if (end == -1) {
                    throw new IllegalArgumentException("Не закрыта кавычка в запросе!");
                }
                tokens.add(src.substring(pos + 1, end));
                pos = end + 1;
            }
            // Операторы сравнения (=, !=, >, <, >=, <=)
            else if (c == '>' || c == '<' || c == '=' || c == '!') {
                String op = "" + c;
                if (pos + 1 < src.length() && src.charAt(pos + 1) == '=') {
                    op += "=";
                    pos++;
                }
                tokens.add(op);
                pos++;
            }
            // Обычное слово или число
            else {
                int start = pos;
                while (pos < src.length() && !isDelimiter(src.charAt(pos))) {
                    pos++;
                }
                tokens.add(src.substring(start, pos));
            }
        }
        return tokens;
    }

    // Достает токен на i-ой позиции
    private String tokenAt(List<String> tokens, int i) {
        if (i < 0 || i >= tokens.size()) {
            throw new IllegalArgumentException("Неожиданный конец запроса");
        }
        return tokens.get(i);
    }
    private boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }
    private boolean isDelimiter(char c) {
        return isSpace(c) || c == ',' || c == '(' || c == ')'
                || c == ';' || c == '\'' || c == '>' || c == '<' || c == '=' || c == '!';
    }
}