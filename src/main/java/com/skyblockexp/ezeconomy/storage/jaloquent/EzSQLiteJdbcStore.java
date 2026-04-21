package com.skyblockexp.ezeconomy.storage.jaloquent;

import java.sql.Connection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQLite-aware {@link EzJdbcStore} that rewrites the MySQL-specific
 * {@code ON DUPLICATE KEY UPDATE col=VALUES(col),...} clause produced by
 * Jaloquent's {@code ModelRepository.save()} into the equivalent SQLite
 * {@code ON CONFLICT(id) DO UPDATE SET col=excluded.col,...} syntax.
 *
 * <p>EzEconomy tables all use {@code id} as the single primary key column,
 * so the conflict target is always {@code id}.
 */
public class EzSQLiteJdbcStore extends EzJdbcStore {

    /**
     * Pattern matching {@code ON DUPLICATE KEY UPDATE} and the assignment list
     * that follows it, capturing each {@code col=VALUES(col)} entry.
     */
    private static final Pattern ON_DUPLICATE =
        Pattern.compile("ON DUPLICATE KEY UPDATE (.+)$", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern matching a single {@code col=VALUES(col)} assignment token.
     */
    private static final Pattern VALUES_ASSIGN =
        Pattern.compile("(\\w+)=VALUES\\(\\w+\\)", Pattern.CASE_INSENSITIVE);

    public EzSQLiteJdbcStore(Connection connection) {
        super(connection);
    }

    /**
     * Transforms {@code INSERT … ON DUPLICATE KEY UPDATE col=VALUES(col),…}
     * to {@code INSERT … ON CONFLICT(id) DO UPDATE SET col=excluded.col,…}.
     *
     * <p>SQL strings that do not contain {@code ON DUPLICATE KEY UPDATE} are
     * returned unchanged.
     */
    @Override
    protected String transformSql(String sql) {
        Matcher m = ON_DUPLICATE.matcher(sql);
        if (!m.find()) {
            return sql;
        }
        String before = sql.substring(0, m.start()).trim();
        String assignList = m.group(1).trim();

        // Build "col=excluded.col,..." from "col=VALUES(col),..."
        StringBuilder sb = new StringBuilder();
        for (String token : assignList.split(",")) {
            Matcher am = VALUES_ASSIGN.matcher(token.trim());
            if (am.find()) {
                String col = am.group(1);
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(col).append("=excluded.").append(col);
            }
        }
        return before + " ON CONFLICT(id) DO UPDATE SET " + sb;
    }
}
