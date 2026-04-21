package com.skyblockexp.ezeconomy.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DbTestHelper {
    private DbTestHelper() {}

    /**
     * Create an in-memory H2 connection configured to be MySQL-compatible for tests.
     *
     * <p>{@code DATABASE_TO_LOWER=TRUE} makes H2 return column labels in lowercase,
     * matching MySQL's behaviour and avoiding case-mismatch with model field lookups
     * (e.g. {@code "balance"} vs H2's default uppercase {@code "BALANCE"}).
     */
    public static Connection createH2MemoryMysql() throws SQLException {
        // MODE=MySQL enables many MySQL-specific syntactic compatibilities.
        // DATABASE_TO_LOWER=TRUE ensures column labels are lowercase, matching MySQL.
        String url = "jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        return DriverManager.getConnection(url, "sa", "");
    }
}
