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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQLiteStorageErrorTest {

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private PreparedStatement preparedStatement;

    @Test
    void testInitializeSchemaFailure() throws SQLException {
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenThrow(new SQLException("Init failed"));

        assertThrows(SQLException.class, () -> new SQLiteStorage(connection));
        verify(connection).close();
    }

    @Test
    void testSaveSymbolsRollback() throws SQLException {
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        
        SQLiteStorage storage = new SQLiteStorage(connection);
        
        doThrow(new SQLException("Batch failed")).when(preparedStatement).executeBatch();
        
        List<Symbol> symbols = List.of(Symbol.builder()
                .name("test")
                .kind(SymbolKind.CLASS)
                .filePath("test.java")
                .line(1)
                .build());

        assertThrows(SQLException.class, () -> storage.saveSymbols(symbols));
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void testSaveSymbolsRollbackFailure() throws SQLException {
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        
        SQLiteStorage storage = new SQLiteStorage(connection);
        
        doThrow(new SQLException("Batch failed")).when(preparedStatement).executeBatch();
        doThrow(new SQLException("Rollback failed")).when(connection).rollback();
        
        List<Symbol> symbols = List.of(Symbol.builder()
                .name("test")
                .kind(SymbolKind.CLASS)
                .filePath("test.java")
                .line(1)
                .build());

        assertThrows(SQLException.class, () -> storage.saveSymbols(symbols));
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
    }
}
