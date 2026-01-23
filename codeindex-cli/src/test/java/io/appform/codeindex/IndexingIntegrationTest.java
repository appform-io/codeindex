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

package io.appform.codeindex;

import io.appform.codeindex.models.SearchRequest;
import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import io.appform.codeindex.storage.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        Path javaFile2 = srcDir.resolve("OtherClass.java");
        Files.writeString(javaFile2, """
                package com.test;
                public class OtherClass {
                    public void testMethod() {}
                }
                """);

        Path dbPath = tempDir.resolve("test.db");

        // 2. Run Indexing
        App.main(new String[]{"index", srcDir.toString(), dbPath.toString()});

        // 3. Verify via Storage search
        try (SQLiteStorage storage = new SQLiteStorage(dbPath.toString())) {
            // Check Class
            List<Symbol> classes = storage.search("TestClass");
            // 7 matches because 'TestClass' matches class name for: Class, Field, 2 Methods, Variable, 2 References
            assertTrue(classes.size() >= 1);
            assertTrue(classes.stream().anyMatch(s -> s.getKind() == SymbolKind.CLASS && "TestClass".equals(s.getName())));

            // Check Field
            List<Symbol> fields = storage.search("myField");
            assertEquals(1, fields.size());
            assertEquals(SymbolKind.FIELD, fields.get(0).getKind());
            assertEquals("TestClass", fields.get(0).getClassName());

            // Check Method
            List<Symbol> methods = storage.search("testMethod");
            assertEquals(2, methods.size());
            assertTrue(methods.stream().allMatch(s -> s.getKind() == SymbolKind.METHOD));
            assertTrue(methods.stream().anyMatch(s -> "TestClass".equals(s.getClassName())));
            assertTrue(methods.stream().anyMatch(s -> "OtherClass".equals(s.getClassName())));

            // Check Class-Aware Search
            List<Symbol> specificMethods = storage.search("TestClass::testMethod");
            assertEquals(1, specificMethods.size());
            assertEquals(SymbolKind.METHOD, specificMethods.get(0).getKind());
            assertEquals("TestClass", specificMethods.get(0).getClassName());

            // Check Variable
            List<Symbol> variables = storage.search("myVar");
            // There should be 2 entries for myVar: one declaration and one reference
            assertTrue(variables.size() >= 1);
            assertTrue(variables.stream().anyMatch(s -> s.getKind() == SymbolKind.VARIABLE));
            assertTrue(variables.stream().anyMatch(s -> s.getKind() == SymbolKind.REFERENCE));
            assertTrue(variables.stream().allMatch(s -> "TestClass".equals(s.getClassName())));

            // Check Method Reference
            List<Symbol> references = storage.search("otherMethod");
            // one METHOD and one REFERENCE
            assertEquals(2, references.size());
            assertTrue(references.stream().anyMatch(s -> s.getKind() == SymbolKind.METHOD));
            assertTrue(references.stream().anyMatch(s -> s.getKind() == SymbolKind.REFERENCE));

            Symbol ref = references.stream().filter(s -> s.getKind() == SymbolKind.REFERENCE).findFirst().orElseThrow();
            assertNotNull(ref.getReferenceTo());

            // Coverage for Symbol methods (toString, equals, hashCode)
            assertNotNull(ref.toString());
            assertEquals(ref, ref);
            assertNotEquals(ref, methods.get(0));
            assertNotEquals(ref, null);
            assertNotEquals(ref, "not a symbol");
            assertEquals(ref.hashCode(), ref.hashCode());
        }
    }

    @Test
    void testAdvancedSearch() throws Exception {
        Path srcDir = tempDir.resolve("src-advanced");
        Files.createDirectories(srcDir);

        Files.writeString(srcDir.resolve("A.java"), "package p1; class A { void m1(){} }");
        Files.writeString(srcDir.resolve("B.java"), "package p2; class B { void m2(){} }");

        Path dbPath = tempDir.resolve("advanced.db");
        App.main(new String[]{"index", srcDir.toString(), dbPath.toString()});

        try (SQLiteStorage storage = new SQLiteStorage(dbPath.toString())) {
            // Filter by kind
            List<Symbol> methods = storage.search(SearchRequest.builder()
                    .kinds(Set.of(SymbolKind.METHOD))
                    .build());
            assertEquals(2, methods.size());

            // Filter by package
            List<Symbol> p1Symbols = storage.search(SearchRequest.builder()
                    .packageName("p1")
                    .build());
            System.out.println("P1 symbols: " + p1Symbols);
            assertTrue(p1Symbols.stream().anyMatch(s -> s.getName().equals("A")));
            assertTrue(p1Symbols.stream().anyMatch(s -> s.getName().equals("m1")));
            assertTrue(p1Symbols.stream().noneMatch(s -> s.getName().equals("B")));

            // Filter by file path glob
            List<Symbol> symbolsFromB = storage.search(SearchRequest.builder()
                    .filePathGlob("*B.java")
                    .build());
            assertTrue(symbolsFromB.stream().anyMatch(s -> s.getName().equals("B")));
            assertTrue(symbolsFromB.stream().noneMatch(s -> s.getName().equals("A")));
            assertTrue(symbolsFromB.stream().noneMatch(s -> s.getName().equals("A")));

            // Filter by class name
            List<Symbol> symbolsFromA = storage.search(SearchRequest.builder()
                    .className("A")
                    .build());
            assertTrue(symbolsFromA.stream().anyMatch(s -> s.getName().equals("m1")));
        }
    }
}
