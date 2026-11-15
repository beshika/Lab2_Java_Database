import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class DBgui extends JFrame {
    private static final String DB_FILE = "workdb.dat";
    private DBmanager db;
    private DBparser parser;
    private JTextField queryField;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JTextArea statusArea;
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DBgui window = new DBgui();
            window.setVisible(true);
        });
    }
    public DBgui() {
        super("Упрощенная СУБД");
        // Попытка загрузить БД с диска при запуске окна
        try {
            db = DBmanager.loadFromFile(DB_FILE);
        }
        catch (IllegalStateException e) {
            db = new DBmanager();
            JOptionPane.showMessageDialog(this,
                    e.getMessage() + "\nСоздана новая пустая база данных.",
                    "Предупреждение при загрузке",
                    JOptionPane.WARNING_MESSAGE
            );
        }
        parser = new DBparser(db);
        initComponents();
    }

    private void initComponents() {
        setSize(850, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        // Строка ввода SQL запроса и кнопка "Выполнить"
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        JLabel promptLabel = new JLabel("SQL-запрос:");
        promptLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        queryField = new JTextField("SELECT * FROM products;");
        queryField.setFont(new Font("Consolas", Font.PLAIN, 14));
        JButton runButton = new JButton("Выполнить");
        runButton.setFont(new Font("SansSerif", Font.BOLD, 13));

        topPanel.add(promptLabel, BorderLayout.WEST);
        topPanel.add(queryField, BorderLayout.CENTER);
        topPanel.add(runButton, BorderLayout.EAST);
        // Таблица для вывода результатов SELECT
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        resultTable = new JTable(tableModel);
        resultTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        resultTable.setRowHeight(24);
        resultTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        JScrollPane scrollPane = new JScrollPane(resultTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        // Строка сообщения об успехе или ошибках
        statusArea = new JTextArea("База данных готова к работе (" + DB_FILE + ")");
        statusArea.setFont(new Font("SansSerif", Font.BOLD, 20));
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setEditable(false);
        statusArea.setOpaque(false);
        statusArea.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusArea, BorderLayout.SOUTH);

        // Выполнение запроса по нажатию на кнопку или на клавишу Enter в поле ввода
        runButton.addActionListener(e -> executeSqlQuery());
        queryField.addActionListener(e -> executeSqlQuery());

        // Сохранение базы на диск при закрытии окна крестиком
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    db.saveToFile(DB_FILE);
                }
                catch (IllegalStateException ex) {
                    JOptionPane.showMessageDialog(
                            DBgui.this,
                            "Не удалось сохранить базу при закрытии: " + ex.getMessage(),
                            "Ошибка сохранения",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    // Считывает текст из поля ввода, передаёт в парсер и обновляет таблицу
    private void executeSqlQuery() {
        String sql = queryField.getText().trim();
        if (sql.isEmpty()) {
            return;
        }
        try {
            List<DBrecord> rows = parser.execute(sql);
            db.saveToFile(DB_FILE); // Изменения сохраняются после каждой команды

            if (rows != null) {
                fillTable(rows);
                statusArea.setForeground(new Color(0, 128, 0));
                statusArea.setText("Запрос SELECT выполнен. Найдено записей: " + rows.size());
            }
            else {
                tableModel.setRowCount(0);
                tableModel.setColumnCount(0);
                statusArea.setForeground(new Color(0, 128, 0));
                statusArea.setText(buildSuccessMessage(sql));
            }
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            statusArea.setForeground(Color.RED);
            statusArea.setText("Ошибка: " + ex.getMessage());
        }
        catch (Exception ex) {
            statusArea.setForeground(Color.RED);
            statusArea.setText("Неожиданная ошибка: " + ex.getMessage());
        }
    }

    // Строит сообщение об успешном выполнении команды, чтобы их различить можно было
    private String buildSuccessMessage(String sql) {
        String cleaned = sql.replace(";", "").trim();
        String[] parts = cleaned.split("[\\s(]+");
        String command = parts[0].toUpperCase();

        switch (command) {
            case "INSERT":
                String insertTable = "";
                if (parts.length > 2) {
                    insertTable = parts[2];
                }
                return "Команда INSERT в таблицу '" + insertTable + "' выполнена успешно";
            case "UPDATE":
                String updateTable = "";
                if (parts.length > 1) {
                    updateTable = parts[1];
                }
                return "Команда UPDATE в таблице '" + updateTable + "' выполнена успешно";
            case "DELETE":
                String deleteTable = "";
                if (parts.length > 2) {
                    deleteTable = parts[2];
                }
                return "Команда DELETE из таблицы '" + deleteTable + "' выполнена успешно";
            case "CREATE":
                String subType = "";
                if (parts.length > 1) {
                    subType = parts[1].toUpperCase();
                }
                String objName = "";
                if (parts.length > 2) {
                    objName = parts[2];
                }
                if ("TABLE".equals(subType)) {
                    return "Таблица '" + objName + "' успешно создана";
                } else if ("INDEX".equals(subType)) {
                    return "Индекс '" + objName + "' успешно создан";
                }
                return "Команда CREATE выполнена успешно";
            case "DROP":
                String dropTable = "";
                if (parts.length > 2) {
                    dropTable = parts[2];
                }
                return "Таблица '" + dropTable + "' успешно удалена (DROP TABLE)";
            default:
                return "Команда " + command + " выполнена успешно";
        }
    }

     // Заполняет JTable данными из списка записей
    private void fillTable(List<DBrecord> rows) {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);
        if (rows.isEmpty()) {
            return;
        }
        List<String> headers = new ArrayList<>(rows.get(0).getFields().keySet());
        for (String header : headers) {
            tableModel.addColumn(header);
        }

        for (DBrecord record : rows) {
            Object[] rowValues = new Object[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                Object val = record.get(headers.get(i));
                if (val == null) {
                    rowValues[i] = "NULL";
                }
                else {
                    rowValues[i] = val;
                }
            }
            tableModel.addRow(rowValues);
        }
    }
}

