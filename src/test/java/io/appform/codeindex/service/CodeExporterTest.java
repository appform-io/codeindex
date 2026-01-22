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

package io.appform.codeindex.service;

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import io.appform.codeindex.storage.SQLiteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void testExportFormats() throws Exception {
        Path dbPath = tempDir.resolve("test.db");
        try (SQLiteStorage storage = new SQLiteStorage(dbPath.toString())) {
            storage.saveSymbols(List.of(
                    Symbol.builder()
                            .name("TestClass")
                            .className("TestClass")
                            .kind(SymbolKind.CLASS)
                            .filePath("TestClass.java")
                            .line(1)
                            .signature("public class TestClass")
                            .build(),
                    Symbol.builder()
                            .name("testMethod")
                            .className("TestClass")
                            .kind(SymbolKind.METHOD)
                            .filePath("TestClass.java")
                            .line(2)
                            .signature("public void testMethod()")
                            .build()
            ));
        }

        CodeExporter exporter = new CodeExporter(dbPath.toString());

        // Test Markdown Export
        Path mdFile = tempDir.resolve("export.md");
        exporter.export(mdFile.toString(), "markdown", null);
        String mdContent = Files.readString(mdFile);
        assertTrue(mdContent.contains("# Project Symbol Index"));
        assertTrue(mdContent.contains("## File: TestClass.java"));
        assertTrue(mdContent.contains("| CLASS | TestClass | 1 | `public class TestClass` |"));
        assertTrue(mdContent.contains("| METHOD | testMethod | 2 | `public void testMethod()` |"));

        // Test XML Export
        Path xmlFile = tempDir.resolve("export.xml");
        exporter.export(xmlFile.toString(), "xml", Set.of(SymbolKind.CLASS));
        String xmlContent = Files.readString(xmlFile);
        assertTrue(xmlContent.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xmlContent.contains("<file path=\"TestClass.java\">"));
        assertTrue(xmlContent.contains("<class name=\"TestClass\">"));
        assertTrue(xmlContent.contains("<symbol kind=\"CLASS\" name=\"TestClass\" line=\"1\" signature=\"public class TestClass\"/>"));
        // Method should be filtered out
        assertTrue(!xmlContent.contains("testMethod"));
    }
}
