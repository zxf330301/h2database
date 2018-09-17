/*
 * Copyright 2004-2018 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.scripts;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.h2.api.ErrorCode;
import org.h2.engine.SysProperties;
import org.h2.jdbc.JdbcConnection;
import org.h2.test.TestAll;
import org.h2.test.TestBase;
import org.h2.test.TestDb;
import org.h2.util.StringUtils;

/**
 * This test runs a SQL script file and compares the output with the expected
 * output.
 */
public class TestScript extends TestDb {

    private static final String BASE_DIR = "org/h2/test/scripts/";

    /** If set to true, the test will exit at the first failure. */
    private boolean failFast;
    /** If set to a value the test will add all executed statements to this list */
    private ArrayList<String> statements;

    private boolean reconnectOften;
    private Connection conn;
    private Statement stat;
    private String fileName;
    private LineNumberReader in;
    private PrintStream out;
    private final ArrayList<String[]> result = new ArrayList<>();
    private String putBack;
    private StringBuilder errors;

    private Random random = new Random(1);

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    /**
     * Get all SQL statements of this file.
     *
     * @param conf the configuration
     * @return the list of statements
     */
    public ArrayList<String> getAllStatements(TestAll conf) throws Exception {
        config = conf;
        ArrayList<String> result = new ArrayList<>(4000);
        try {
            statements = result;
            test();
        } finally {
            this.statements = null;
        }
        return result;
    }

    @Override
    public boolean isEnabled() {
        if (config.networked && config.big) {
            return false;
        }
        return true;
    }

    @Override
    public void test() throws Exception {
        reconnectOften = !config.memory && config.big;

        testScript("testScript.sql");
        testScript("comments.sql");
        testScript("derived-column-names.sql");
        testScript("distinct.sql");
        testScript("dual.sql");
        testScript("indexes.sql");
        testScript("information_schema.sql");
        testScript("joins.sql");
        testScript("range_table.sql");
        testScript("altertable-index-reuse.sql");
        testScript("altertable-fk.sql");
        testScript("default-and-on_update.sql");
        testScript("query-optimisations.sql");
        String decimal2;
        if (SysProperties.BIG_DECIMAL_IS_DECIMAL) {
            decimal2 = "decimal_decimal";
        } else {
            decimal2 = "decimal_numeric";
        }

        for (String s : new String[] { "array", "bigint", "binary", "blob",
                "boolean", "char", "clob", "date", "decimal", decimal2, "double", "enum",
                "geometry", "identity", "int", "interval", "other", "real", "smallint",
                "time", "timestamp-with-timezone", "timestamp", "tinyint",
                "uuid", "varchar", "varchar-ignorecase" }) {
            testScript("datatypes/" + s + ".sql");
        }
        for (String s : new String[] { "alterTableAdd", "alterTableDropColumn", "alterTableRename",
                "createAlias", "createSequence", "createSynonym", "createTable", "createTrigger", "createView",
                "dropDomain", "dropIndex", "dropSchema", "truncateTable" }) {
            testScript("ddl/" + s + ".sql");
        }
        for (String s : new String[] { "error_reporting", "insertIgnore", "merge", "mergeUsing", "replace",
                "script", "select", "show", "with" }) {
            testScript("dml/" + s + ".sql");
        }
        for (String s : new String[] { "help" }) {
            testScript("other/" + s + ".sql");
        }
        for (String s : new String[] { "array-agg", "avg", "bit-and", "bit-or", "count", "envelope",
                "group-concat", "max", "median", "min", "mode", "selectivity", "stddev-pop",
                "stddev-samp", "sum", "var-pop", "var-samp" }) {
            testScript("functions/aggregate/" + s + ".sql");
        }
        for (String s : new String[] { "abs", "acos", "asin", "atan", "atan2",
                "bitand", "bitget", "bitor", "bitxor", "ceil", "compress",
                "cos", "cosh", "cot", "decrypt", "degrees", "encrypt", "exp",
                "expand", "floor", "hash", "length", "log", "mod", "ora-hash", "pi",
                "power", "radians", "rand", "random-uuid", "round",
                "roundmagic", "secure-rand", "sign", "sin", "sinh", "sqrt",
                "tan", "tanh", "truncate", "zero" }) {
            testScript("functions/numeric/" + s + ".sql");
        }
        for (String s : new String[] { "ascii", "bit-length", "char", "concat",
                "concat-ws", "difference", "hextoraw", "insert", "instr",
                "left", "length", "locate", "lower", "lpad", "ltrim",
                "octet-length", "position", "rawtohex", "regexp-like",
                "regex-replace", "repeat", "replace", "right", "rpad", "rtrim",
                "soundex", "space", "stringdecode", "stringencode",
                "stringtoutf8", "substring", "to-char", "translate", "trim",
                "upper", "utf8tostring", "xmlattr", "xmlcdata", "xmlcomment",
                "xmlnode", "xmlstartdoc", "xmltext" }) {
            testScript("functions/string/" + s + ".sql");
        }
        for (String s : new String[] { "array-contains", "array-get",
                "array-length", "autocommit", "cancel-session", "casewhen",
                "cast", "coalesce", "convert", "csvread", "csvwrite", "currval",
                "database-path", "database", "decode", "disk-space-used",
                "file-read", "file-write", "greatest", "h2version", "identity",
                "ifnull", "least", "link-schema", "lock-mode", "lock-timeout",
                "memory-free", "memory-used", "nextval", "nullif", "nvl2",
                "readonly", "rownum", "schema", "scope-identity", "session-id",
                "set", "table", "transaction-id", "truncate-value", "user" }) {
            testScript("functions/system/" + s + ".sql");
        }
        for (String s : new String[] { "add_months", "current_date", "current_timestamp",
                "current-time", "dateadd", "datediff", "dayname",
                "day-of-month", "day-of-week", "day-of-year", "extract",
                "formatdatetime", "hour", "minute", "month", "monthname",
                "parsedatetime", "quarter", "second", "truncate", "week", "year", "date_trunc" }) {
            testScript("functions/timeanddate/" + s + ".sql");
        }
        for (String s : new String[] { "row_number" }) {
            testScript("functions/window/" + s + ".sql");
        }

        deleteDb("script");
        System.out.flush();
    }

    private void testScript(String scriptFileName) throws Exception {
        deleteDb("script");

        // Reset all the state in case there is anything left over from the previous file
        // we processed.
        conn = null;
        stat = null;
        fileName = null;
        in = null;
        out = null;
        result.clear();
        putBack = null;
        errors = null;

        if (statements == null) {
            println("Running commands in " + scriptFileName);
        }
        final String outFile = "test.out.txt";
        conn = getConnection("script");
        stat = conn.createStatement();
        out = new PrintStream(new FileOutputStream(outFile));
        errors = new StringBuilder();
        testFile(BASE_DIR + scriptFileName,
                !scriptFileName.equals("functions/numeric/rand.sql") &&
                !scriptFileName.equals("functions/system/set.sql") &&
                !scriptFileName.equals("ddl/createAlias.sql") &&
                !scriptFileName.equals("ddl/dropSchema.sql"));
        conn.close();
        out.close();
        if (errors.length() > 0) {
            throw new Exception("errors in " + scriptFileName + " found");
        }
        // new File(outFile).delete();
    }

    private String readLine() throws IOException {
        if (putBack != null) {
            String s = putBack;
            putBack = null;
            return s;
        }
        while (true) {
            String s = in.readLine();
            if (s == null) {
                return null;
            }
            if (s.startsWith("#")) {
                int end = s.indexOf('#', 1);
                if (end < 3) {
                    fail("Bad line \"" + s + '\"');
                }
                boolean val;
                switch (s.charAt(1)) {
                case '+':
                    val = true;
                    break;
                case '-':
                    val = false;
                    break;
                default:
                    fail("Bad line \"" + s + '\"');
                    return null;
                }
                String flag = s.substring(2, end);
                s = s.substring(end + 1);
                switch (flag) {
                case "mvStore":
                    if (config.mvStore == val) {
                        break;
                    } else {
                        continue;
                    }
                default:
                    fail("Unknown flag \"" + flag + '\"');
                }
            }
            s = s.trim();
            if (s.length() > 0) {
                return s;
            }
        }
    }

    private void testFile(String inFile, boolean allowReconnect) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(inFile);
        if (is == null) {
            throw new IOException("could not find " + inFile);
        }
        fileName = inFile;
        in = new LineNumberReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder buff = new StringBuilder();
        while (true) {
            String sql = readLine();
            if (sql == null) {
                break;
            }
            if (sql.startsWith("--")) {
                write(sql);
            } else if (sql.startsWith(">")) {
                addWriteResultError("<command>", sql);
            } else if (sql.endsWith(";")) {
                write(sql);
                buff.append(sql, 0, sql.length() - 1);
                sql = buff.toString();
                buff.setLength(0);
                process(sql, allowReconnect);
            } else if (sql.equals("@reconnect")) {
                if (buff.length() > 0) {
                    addWriteResultError("<command>", sql);
                } else {
                    if (!config.memory) {
                        reconnect(conn.getAutoCommit());
                    }
                }
            } else {
                write(sql);
                buff.append(sql);
                buff.append('\n');
            }
        }
    }

    private boolean containsTempTables() throws SQLException {
        ResultSet rs = conn.getMetaData().getTables(null, null, null,
                new String[] { "TABLE" });
        while (rs.next()) {
            String sql = rs.getString("SQL");
            if (sql != null) {
                if (sql.contains("TEMPORARY")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void process(String sql, boolean allowReconnect) throws Exception {
        if (allowReconnect && reconnectOften) {
            if (!containsTempTables() && ((JdbcConnection) conn).isRegularMode()
                    && conn.getSchema().equals("PUBLIC")) {
                boolean autocommit = conn.getAutoCommit();
                if (autocommit && random.nextInt(10) < 1) {
                    // reconnect 10% of the time
                    reconnect(autocommit);
                }
            }
        }
        if (statements != null) {
            statements.add(sql);
        }
        if (sql.indexOf('?') == -1) {
            processStatement(sql);
        } else {
            String param = readLine();
            write(param);
            if (!param.equals("{")) {
                throw new AssertionError("expected '{', got " + param + " in " + sql);
            }
            try {
                PreparedStatement prep = conn.prepareStatement(sql);
                int count = 0;
                while (true) {
                    param = readLine();
                    write(param);
                    if (param.startsWith("}")) {
                        break;
                    }
                    count += processPrepared(sql, prep, param);
                }
                writeResult(sql, "update count: " + count, null);
            } catch (SQLException e) {
                writeException(sql, e);
            }
        }
        write("");
    }

    private void reconnect(boolean autocommit) throws SQLException {
        conn.close();
        conn = getConnection("script");
        conn.setAutoCommit(autocommit);
        stat = conn.createStatement();
    }

    private static void setParameter(PreparedStatement prep, int i, String param)
            throws SQLException {
        if (param.equalsIgnoreCase("null")) {
            param = null;
        }
        prep.setString(i, param);
    }

    private int processPrepared(String sql, PreparedStatement prep, String param)
            throws Exception {
        try {
            StringBuilder buff = new StringBuilder();
            int index = 0;
            for (int i = 0; i < param.length(); i++) {
                char c = param.charAt(i);
                if (c == ',') {
                    setParameter(prep, ++index, buff.toString());
                    buff.setLength(0);
                } else if (c == '"') {
                    while (true) {
                        c = param.charAt(++i);
                        if (c == '"') {
                            break;
                        }
                        buff.append(c);
                    }
                } else if (c > ' ') {
                    buff.append(c);
                }
            }
            if (buff.length() > 0) {
                setParameter(prep, ++index, buff.toString());
            }
            if (prep.execute()) {
                writeResultSet(sql, prep.getResultSet());
                return 0;
            }
            return prep.getUpdateCount();
        } catch (SQLException e) {
            writeException(sql, e);
            return 0;
        }
    }

    private int processStatement(String sql) throws Exception {
        try {
            if (stat.execute(sql)) {
                writeResultSet(sql, stat.getResultSet());
            } else {
                int count = stat.getUpdateCount();
                writeResult(sql, count < 1 ? "ok" : "update count: " + count, null);
            }
        } catch (SQLException e) {
            writeException(sql, e);
        }
        return 0;
    }

    private static String formatString(String s) {
        if (s == null) {
            return "null";
        }
        s = StringUtils.replaceAll(s, "\r\n", "\n");
        s = s.replace('\n', ' ');
        s = StringUtils.replaceAll(s, "    ", " ");
        while (true) {
            String s2 = StringUtils.replaceAll(s, "  ", " ");
            if (s2.length() == s.length()) {
                break;
            }
            s = s2;
        }
        return s;
    }

    private void writeResultSet(String sql, ResultSet rs) throws Exception {
        boolean ordered = StringUtils.toLowerEnglish(sql).contains("order by");
        ResultSetMetaData meta = rs.getMetaData();
        int len = meta.getColumnCount();
        int[] max = new int[len];
        result.clear();
        while (rs.next()) {
            String[] row = new String[len];
            for (int i = 0; i < len; i++) {
                String data = formatString(rs.getString(i + 1));
                if (max[i] < data.length()) {
                    max[i] = data.length();
                }
                row[i] = data;
            }
            result.add(row);
        }
        String[] head = new String[len];
        for (int i = 0; i < len; i++) {
            String label = formatString(meta.getColumnLabel(i + 1));
            if (max[i] < label.length()) {
                max[i] = label.length();
            }
            head[i] = label;
        }
        rs.close();
        String line = readLine();
        putBack = line;
        if (line != null && line.startsWith(">> ")) {
            switch (result.size()) {
            case 0:
                writeResult(sql, "<no result>", null, ">> ");
                return;
            case 1:
                String[] row = result.get(0);
                if (row.length == 1) {
                    writeResult(sql, row[0], null, ">> ");
                } else {
                    writeResult(sql, "<row with " + row.length + " values>", null, ">> ");
                }
                return;
            default:
                writeResult(sql, "<" + result.size() + " rows>", null, ">> ");
                return;
            }
        }
        writeResult(sql, format(head, max), null);
        writeResult(sql, format(null, max), null);
        String[] array = new String[result.size()];
        for (int i = 0; i < result.size(); i++) {
            array[i] = format(result.get(i), max);
        }
        if (!ordered) {
            sort(array);
        }
        int i = 0;
        for (; i < array.length; i++) {
            writeResult(sql, array[i], null);
        }
        writeResult(sql, (ordered ? "rows (ordered): " : "rows: ") + i, null);
    }

    private static String format(String[] row, int[] max) {
        int length = max.length;
        StringBuilder buff = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                buff.append(' ');
            }
            if (row == null) {
                for (int j = 0; j < max[i]; j++) {
                    buff.append('-');
                }
            } else {
                int len = row[i].length();
                buff.append(row[i]);
                if (i < length - 1) {
                    for (int j = len; j < max[i]; j++) {
                        buff.append(' ');
                    }
                }
            }
        }
        return buff.toString();
    }

    /** Convert the error code to a symbolic name from ErrorCode. */
    private static final Map<Integer, String> ERROR_CODE_TO_NAME = new HashMap<>(256);
    static {
        try {
            for (Field field : ErrorCode.class.getDeclaredFields()) {
                if (field.getModifiers() == (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL)) {
                    ERROR_CODE_TO_NAME.put(field.getInt(null), field.getName());
                }
            }
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void writeException(String sql, SQLException ex) throws Exception {
        writeResult(sql, "exception " + ERROR_CODE_TO_NAME.get(ex.getErrorCode()), ex);
    }

    private void writeResult(String sql, String s, SQLException ex) throws Exception {
        writeResult(sql, s, ex, "> ");
    }

    private void writeResult(String sql, String s, SQLException ex, String prefix) throws Exception {
        assertKnownException(sql, ex);
        s = (prefix + s).trim();
        String compare = readLine();
        if (compare != null && compare.startsWith(">")) {
            if (!compare.equals(s)) {
                if (reconnectOften && sql.toUpperCase().startsWith("EXPLAIN")) {
                    return;
                }
                addWriteResultError(compare, s);
                if (ex != null) {
                    TestBase.logError("script", ex);
                }
                if (failFast) {
                    conn.close();
                    System.exit(1);
                }
            }
        } else {
            addWriteResultError("<nothing>", s);
            putBack = compare;
        }
        write(s);
    }

    private void addWriteResultError(String expected, String got) {
        int idx = errors.length();
        errors.append(fileName).append('\n');
        errors.append("line: ").append(in.getLineNumber()).append('\n');
        errors.append("exp: ").append(expected).append('\n');
        errors.append("got: ").append(got).append('\n');
        TestBase.logErrorMessage(errors.substring(idx));
    }

    private void write(String s) {
        out.println(s);
    }

    private static void sort(String[] a) {
        for (int i = 1, j, len = a.length; i < len; i++) {
            String t = a[i];
            for (j = i - 1; j >= 0 && t.compareTo(a[j]) < 0; j--) {
                a[j + 1] = a[j];
            }
            a[j + 1] = t;
        }
    }

}
