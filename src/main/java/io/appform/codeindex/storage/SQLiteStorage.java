package io.appform.codeindex.storage;

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SQLiteStorage implements AutoCloseable {
    private final Connection connection;

    public SQLiteStorage(String dbPath) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        this.connection = DriverManager.getConnection(url);
        try {
            initializeSchema();
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeEx) {
                e.addSuppressed(closeEx);
            }
            throw e;
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS symbols (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    line INTEGER NOT NULL,
                    signature TEXT,
                    reference_to TEXT
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_reference_to ON symbols(reference_to)");
        }
    }

    public void saveSymbols(List<Symbol> symbols) throws SQLException {
        String sql = "INSERT INTO symbols (name, kind, file_path, line, signature, reference_to) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (Symbol symbol : symbols) {
                pstmt.setString(1, symbol.getName());
                pstmt.setString(2, symbol.getKind().name());
                pstmt.setString(3, symbol.getFilePath());
                pstmt.setInt(4, symbol.getLine());
                pstmt.setString(5, symbol.getSignature());
                pstmt.setString(6, symbol.getReferenceTo());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                log.error("Error resetting auto-commit", e);
            }
        }
    }

    public List<Symbol> search(String query) throws SQLException {
        return search(query, 1000);
    }

    public List<Symbol> search(String query, int limit) throws SQLException {
        String sql = "SELECT name, kind, file_path, line, signature, reference_to FROM symbols WHERE name LIKE ? LIMIT ?";
        List<Symbol> results = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, "%" + query + "%");
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(Symbol.builder()
                            .name(rs.getString("name"))
                            .kind(SymbolKind.valueOf(rs.getString("kind")))
                            .filePath(rs.getString("file_path"))
                            .line(rs.getInt("line"))
                            .signature(rs.getString("signature"))
                            .referenceTo(rs.getString("reference_to"))
                            .build());
                }
            }
        }
        return results;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
