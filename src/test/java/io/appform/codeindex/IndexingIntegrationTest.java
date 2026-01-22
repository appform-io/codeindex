package io.appform.codeindex;

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import io.appform.codeindex.storage.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexingIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testEndToEndIndexing() throws Exception {
        // 1. Create dummy Java files
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        
        Path javaFile1 = srcDir.resolve("TestClass.java");
        Files.writeString(javaFile1, """
            package com.test;
            public class TestClass {
                private String myField;
                public void testMethod() {
                    int myVar = 10;
                    System.out.println(myVar);
                    otherMethod();
                }
                public void otherMethod() {}
            }
        """);

        Path dbPath = tempDir.resolve("test.db");

        // 2. Run Indexing
        App.main(new String[]{"index", srcDir.toString(), dbPath.toString()});

        // 3. Verify via Storage search
        try (SQLiteStorage storage = new SQLiteStorage(dbPath.toString())) {
            // Check Class
            List<Symbol> classes = storage.search("TestClass");
            assertEquals(1, classes.size());
            assertEquals(SymbolKind.CLASS, classes.get(0).getKind());

            // Check Field
            List<Symbol> fields = storage.search("myField");
            assertEquals(1, fields.size());
            assertEquals(SymbolKind.FIELD, fields.get(0).getKind());

            // Check Method
            List<Symbol> methods = storage.search("testMethod");
            assertEquals(1, methods.size());
            assertEquals(SymbolKind.METHOD, methods.get(0).getKind());

            // Check Variable
            List<Symbol> variables = storage.search("myVar");
            // There should be 2 entries for myVar: one declaration and one reference
            assertTrue(variables.size() >= 1);
            assertTrue(variables.stream().anyMatch(s -> s.getKind() == SymbolKind.VARIABLE));
            assertTrue(variables.stream().anyMatch(s -> s.getKind() == SymbolKind.REFERENCE));

            // Check Method Reference
            List<Symbol> references = storage.search("otherMethod");
            // one METHOD and one REFERENCE
            assertEquals(2, references.size());
            assertTrue(references.stream().anyMatch(s -> s.getKind() == SymbolKind.METHOD));
            assertTrue(references.stream().anyMatch(s -> s.getKind() == SymbolKind.REFERENCE));
            
            Symbol ref = references.stream().filter(s -> s.getKind() == SymbolKind.REFERENCE).findFirst().orElseThrow();
            assertNotNull(ref.getReferenceTo());
        }
    }

    @Test
    void testSearchFunctionality() throws Exception {
        Path dbPath = tempDir.resolve("search_test.db");
        try (SQLiteStorage storage = new SQLiteStorage(dbPath.toString())) {
            storage.saveSymbols(List.of(
                Symbol.builder().name("Alpha").kind(SymbolKind.CLASS).filePath("F1").line(1).signature("S1").referenceTo(null).build(),
                Symbol.builder().name("Beta").kind(SymbolKind.METHOD).filePath("F2").line(2).signature("S2").referenceTo(null).build()
            ));

            List<Symbol> results = storage.search("lp"); // matches Alpha
            assertEquals(1, results.size());
            assertEquals("Alpha", results.get(0).getName());
        }
    }
}
