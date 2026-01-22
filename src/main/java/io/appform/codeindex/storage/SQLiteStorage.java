/*
 * Copyright 2026 codeindex contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        this(DriverManager.getConnection("jdbc:sqlite:" + dbPath));
    }

    SQLiteStorage(Connection connection) throws SQLException {
        this.connection = connection;
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
                    class_name TEXT,
                    kind TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    line INTEGER NOT NULL,
                    signature TEXT,
                    reference_to TEXT
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_reference_to ON symbols(reference_to)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_class_name ON symbols(class_name)");
        }
    }

    public void saveSymbols(List<Symbol> symbols) throws SQLException {
        String sql = "INSERT INTO symbols (name, class_name, kind, file_path, line, signature, reference_to) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (Symbol symbol : symbols) {
                pstmt.setString(1, symbol.getName());
                pstmt.setString(2, symbol.getClassName());
                pstmt.setString(3, symbol.getKind().name());
                pstmt.setString(4, symbol.getFilePath());
                pstmt.setInt(5, symbol.getLine());
                pstmt.setString(6, symbol.getSignature());
                pstmt.setString(7, symbol.getReferenceTo());
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
        String sql;
        String searchTerm;
        if (query.contains("::")) {
            String[] parts = query.split("::", 2);
            sql = "SELECT name, class_name, kind, file_path, line, signature, reference_to FROM symbols WHERE class_name LIKE ? AND name LIKE ? LIMIT ?";
            searchTerm = parts[1];
            String classTerm = parts[0];
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, "%" + classTerm + "%");
                pstmt.setString(2, "%" + searchTerm + "%");
                pstmt.setInt(3, limit);
                return executeSearch(pstmt);
            }
        } else {
            sql = "SELECT name, class_name, kind, file_path, line, signature, reference_to FROM symbols WHERE name LIKE ? OR class_name LIKE ? LIMIT ?";
            searchTerm = query;
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, "%" + searchTerm + "%");
                pstmt.setString(2, "%" + searchTerm + "%");
                pstmt.setInt(3, limit);
                return executeSearch(pstmt);
            }
        }
    }

    private List<Symbol> executeSearch(PreparedStatement pstmt) throws SQLException {
        List<Symbol> results = new ArrayList<>();
        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                results.add(Symbol.builder()
                        .name(rs.getString("name"))
                        .className(rs.getString("class_name"))
                        .kind(SymbolKind.valueOf(rs.getString("kind")))
                        .filePath(rs.getString("file_path"))
                        .line(rs.getInt("line"))
                        .signature(rs.getString("signature"))
                        .referenceTo(rs.getString("reference_to"))
                        .build());
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
