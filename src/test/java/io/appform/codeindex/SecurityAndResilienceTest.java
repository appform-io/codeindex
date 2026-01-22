package io.appform.codeindex;

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import io.appform.codeindex.service.CodeIndexer;
import io.appform.codeindex.storage.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAndResilienceTest {

    @TempDir
    Path tempDir;

    @Test
    void testFileCrawlerInvalidPath() {
        io.appform.codeindex.crawler.FileCrawler crawler = new io.appform.codeindex.crawler.FileCrawler();
        assertThrows(IllegalArgumentException.class, () -> crawler.crawl(tempDir.resolve("non_existent").toString()));
    }

    @Test
    void testAppMainUsage() {
        assertDoesNotThrow(() -> App.main(new String[]{}));
    }

    @Test
    void testAppSearchCommand() throws Exception {
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("A.java"), "public class A {}");
        Path dbPath = tempDir.resolve("app_search.db");
        
        App.main(new String[]{"index", srcDir.toString(), dbPath.toString()});
        // Test search command
        assertDoesNotThrow(() -> App.main(new String[]{"search", "A", dbPath.toString()}));
    }

    @Test
    void testAppUnknownCommand() {
        assertDoesNotThrow(() -> App.main(new String[]{"unknown", "arg1", "arg2"}));
    }

    @Test
    void testSymbolGetters() {
        Symbol s = Symbol.builder()
                .name("N")
                .kind(SymbolKind.CLASS)
                .filePath("F")
                .line(1)
                .signature("S")
                .referenceTo("R")
                .build();
        assertEquals("N", s.getName());
        assertEquals(SymbolKind.CLASS, s.getKind());
        assertEquals("F", s.getFilePath());
        assertEquals(1, s.getLine());
        assertEquals("S", s.getSignature());
        assertEquals("R", s.getReferenceTo());
    }

    @Test
    void testIndexingContinuesOnParseError() throws Exception {
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        
        // 1. Valid file
        Files.writeString(srcDir.resolve("Valid.java"), "public class Valid {}");
        
        // 2. Corrupt/Invalid Java file
        Files.writeString(srcDir.resolve("Corrupt.java"), "this is not java code {");
        
        Path dbPath = tempDir.resolve("resilience.db");
        CodeIndexer indexer = new CodeIndexer(dbPath.toString());
        
        // Should not throw exception
        assertDoesNotThrow(() -> indexer.index(srcDir.toString()));
        
        // Should have indexed the valid one
        try (SQLiteStorage storage = new SQLiteStorage(dbPath.toString())) {
            List<Symbol> results = storage.search("Valid");
            assertEquals(1, results.size());
        }
    }

    @Test
    void testRelativePathPortability() throws Exception {
        Path srcDir = tempDir.resolve("project_root");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("A.java"), "public class A {}");
        
        Path dbPath = tempDir.resolve("portable.db");
        CodeIndexer indexer = new CodeIndexer(dbPath.toString());
        indexer.index(srcDir.toString());
        
        try (SQLiteStorage storage = new SQLiteStorage(dbPath.toString())) {
            List<Symbol> results = storage.search("A");
            assertEquals(1, results.size());
            // Should be "A.java" not "/tmp/.../A.java"
            assertEquals("A.java", results.get(0).getFilePath());
        }
    }

    @Test
    void testSearchLimitPreventsOOM() throws Exception {
        Path dbPath = tempDir.resolve("limit.db");
        try (SQLiteStorage storage = new SQLiteStorage(dbPath.toString())) {
            // Insert 1100 symbols
            for (int i = 0; i < 1100; i++) {
                storage.saveSymbols(List.of(
                    Symbol.builder()
                        .name("CommonName")
                        .kind(SymbolKind.CLASS)
                        .filePath("F" + i)
                        .line(i)
                        .signature("S" + i)
                        .build()
                ));
            }
            
            // Search should return exactly 1000 (the default limit)
            List<Symbol> results = storage.search("CommonName");
            assertEquals(1000, results.size());
            
            // Explicit limit should work
            List<Symbol> resultsWithSmallLimit = storage.search("CommonName", 10);
            assertEquals(10, resultsWithSmallLimit.size());
        }
    }
}
