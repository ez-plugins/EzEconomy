package com.skyblockexp.ezeconomy.storage.jaloquent;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EzJdbcStoreRecoveryTest {

    @Test
    void executeUpdate_connectionFailure_refreshesAndRetriesOnce() throws Exception {
        Connection broken = mock(Connection.class);
        when(broken.prepareStatement(anyString()))
                .thenThrow(new SQLTransientConnectionException("reset", "08006"));
        when(broken.getAutoCommit()).thenReturn(true);

        Connection recovered = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(recovered.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(1);

        AtomicInteger refreshCalls = new AtomicInteger(0);
        EzJdbcStore store = new EzJdbcStore(broken, () -> {
            refreshCalls.incrementAndGet();
            return recovered;
        });

        int updated = store.executeUpdate("UPDATE balances SET balance = ? WHERE id = ?", Arrays.<Object>asList(5.0, "id-1"));

        assertEquals(1, updated);
        assertEquals(1, refreshCalls.get());
        verify(ps).setObject(1, 5.0);
        verify(ps).setObject(2, "id-1");
    }

    @Test
    void query_communicationsFailure_refreshesAndReturnsRows() throws Exception {
        Connection broken = mock(Connection.class);
        when(broken.prepareStatement(anyString()))
                .thenThrow(new SQLException("Communications link failure", "08S01"));
        when(broken.getAutoCommit()).thenReturn(true);

        Connection recovered = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);

        when(recovered.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnLabel(1)).thenReturn("id");
        when(rs.next()).thenReturn(true, false);
        when(rs.getObject(1)).thenReturn("row-1");

        AtomicInteger refreshCalls = new AtomicInteger(0);
        EzJdbcStore store = new EzJdbcStore(broken, () -> {
            refreshCalls.incrementAndGet();
            return recovered;
        });

        List<Map<String, Object>> rows = store.query("SELECT id FROM players WHERE id = ?", Collections.<Object>singletonList("row-1"));

        assertEquals(1, rows.size());
        assertEquals("row-1", rows.get(0).get("id"));
        assertEquals(1, refreshCalls.get());
        verify(ps).setObject(1, "row-1");
    }

    @Test
    void executeUpdate_connectionFailureInsideTransaction_doesNotRefresh() throws Exception {
        Connection broken = mock(Connection.class);
        when(broken.prepareStatement(anyString()))
                .thenThrow(new SQLTransientConnectionException("reset", "08006"));
        when(broken.getAutoCommit()).thenReturn(false);

        AtomicInteger refreshCalls = new AtomicInteger(0);
        EzJdbcStore store = new EzJdbcStore(broken, () -> {
            refreshCalls.incrementAndGet();
            return mock(Connection.class);
        });

        assertThrows(SQLTransientConnectionException.class,
                () -> store.executeUpdate("UPDATE balances SET balance = ?", Collections.<Object>singletonList(1.0)));
        assertEquals(0, refreshCalls.get());
        verify(broken, never()).isClosed();
    }
}
