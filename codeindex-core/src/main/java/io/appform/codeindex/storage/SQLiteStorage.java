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

import io.appform.codeindex.models.SearchRequest;
import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class SQLiteStorage implements AutoCloseable {
    private final Connection connection;

    public SQLiteStorage(String dbPath) throws SQLException {
        this(DriverManager.getConnection("jdbc:sqlite:" + dbPath));
    }

    SQLiteStorage(Connection connection) throws SQLException {
        this.connection = connection;
        try {
            tuneDatabase();
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

    private void tuneDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA synchronous = NORMAL");
            stmt.execute("PRAGMA cache_size = -20000"); // 20MB
            stmt.execute("PRAGMA temp_store = MEMORY");
            stmt.execute("PRAGMA mmap_size = 30000000000"); // Up to 30GB mmap
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS symbols (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        class_name TEXT,
                        package_name TEXT,
                        kind TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        line INTEGER NOT NULL,
                        signature TEXT,
                        reference_to TEXT
                    )
                    """);
            stmt.execute("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS symbols_fts USING fts5(
                        name, class_name, package_name,
                        content='symbols',
                        content_rowid='id'
                    )
                    """);
            stmt.execute("""
                    CREATE TRIGGER IF NOT EXISTS symbols_ai AFTER INSERT ON symbols BEGIN
                        INSERT INTO symbols_fts(rowid, name, class_name, package_name)
                        VALUES (new.id, new.name, new.class_name, new.package_name);
                    END
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_name ON symbols(name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_reference_to ON symbols(reference_to)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_class_name ON symbols(class_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_symbols_package_name ON symbols(package_name)");
        }
    }

    public void saveSymbols(List<Symbol> symbols) throws SQLException {
        final var sql = "INSERT INTO symbols (name, class_name, package_name, kind, file_path, line, signature, reference_to) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        final var batchSize = 1000;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            int count = 0;
            for (Symbol symbol : symbols) {
                pstmt.setString(1, symbol.getName());
                pstmt.setString(2, symbol.getClassName());
                pstmt.setString(3, symbol.getPackageName());
                pstmt.setString(4, symbol.getKind().name());
                pstmt.setString(5, symbol.getFilePath());
                pstmt.setInt(6, symbol.getLine());
                pstmt.setString(7, symbol.getSignature());
                pstmt.setString(8, symbol.getReferenceTo());
                pstmt.addBatch();

                if (++count % batchSize == 0) {
                    pstmt.executeBatch();
                }
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
        return search(SearchRequest.builder()
                .query(query)
                .limit(limit)
                .build());
    }

    public List<Symbol> search(SearchRequest request) throws SQLException {
        final var sql = new StringBuilder("SELECT name, class_name, package_name, kind, file_path, line, signature, reference_to FROM symbols WHERE id IN (SELECT rowid FROM symbols_fts WHERE symbols_fts MATCH ?)");
        final var params = new ArrayList<>();

        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            if (request.getQuery().contains("::")) {
                final var parts = request.getQuery().split("::");
                final var containerTerm = parts[0];
                final var symbolTerm = parts[1];
                params.add(String.format("name:%s* AND (class_name:%s* OR package_name:%s*)", symbolTerm, containerTerm, containerTerm));
            } else {
                params.add(String.format("name:%s* OR class_name:%s* OR package_name:%s*", request.getQuery(), request.getQuery(), request.getQuery()));
            }
        } else {
            // Fallback for empty query if other filters are present
            sql.setLength(0);
            sql.append("SELECT name, class_name, package_name, kind, file_path, line, signature, reference_to FROM symbols WHERE 1=1");
        }

        if (request.getClassName() != null && !request.getClassName().isBlank()) {
            sql.append(" AND class_name LIKE ?");
            params.add("%" + request.getClassName() + "%");
        }

        if (request.getPackageName() != null && !request.getPackageName().isBlank()) {
            sql.append(" AND package_name LIKE ?");
            params.add("%" + request.getPackageName() + "%");
        }

        if (request.getFilePathGlob() != null && !request.getFilePathGlob().isBlank()) {
            // SQLite doesn't have native GLOB support in the same way as file systems,
            // but it has a GLOB operator. We'll use LIKE for simplicity or GLOB if preferred.
            sql.append(" AND file_path GLOB ?");
            params.add(request.getFilePathGlob());
        }

        if (request.getKinds() != null && !request.getKinds().isEmpty()) {
            sql.append(" AND kind IN (")
                    .append(IntStream.range(0, request.getKinds().size())
                            .mapToObj(i -> "?")
                            .collect(Collectors.joining(",")))
                    .append(")");
            request.getKinds().forEach(kind -> params.add(kind.name()));
        }

        sql.append(" LIMIT ?");
        params.add(request.getLimit());

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                final var param = params.get(i);
                if (param instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) param);
                } else {
                    pstmt.setString(i + 1, (String) param);
                }
            }
            return executeSearch(pstmt);
        }
    }

    private List<Symbol> executeSearch(PreparedStatement pstmt) throws SQLException {
        final var results = new ArrayList<Symbol>();
        try (ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                results.add(Symbol.builder()
                        .name(rs.getString("name"))
                        .className(rs.getString("class_name"))
                        .packageName(rs.getString("package_name"))
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

    public List<Symbol> getAllSymbols(Set<SymbolKind> kinds) throws SQLException {
        final var sql = new StringBuilder("SELECT name, class_name, package_name, kind, file_path, line, signature, reference_to FROM symbols");
        if (kinds != null && !kinds.isEmpty()) {
            sql.append(" WHERE kind IN (");
            for (int i = 0; i < kinds.size(); i++) {
                sql.append("?");
                if (i < kinds.size() - 1) {
                    sql.append(",");
                }
            }
            sql.append(")");
        }
        sql.append(" ORDER BY file_path, line");

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            if (kinds != null && !kinds.isEmpty()) {
                int i = 1;
                for (SymbolKind kind : kinds) {
                    pstmt.setString(i++, kind.name());
                }
            }
            return executeSearch(pstmt);
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
