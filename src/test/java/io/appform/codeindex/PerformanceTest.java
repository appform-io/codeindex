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

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.parser.JavaParser;
import io.appform.codeindex.parser.ParserRegistry;
import io.appform.codeindex.service.CodeIndexer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("performance")
class PerformanceTest {

    @TempDir
    Path tempDir;

    @Test
    void testIndexingPerformance() throws Exception {
        Path srcDir = tempDir.resolve("perf_src");
        Files.createDirectories(srcDir);
        
        // Generate 100 files with 10 methods each
        int fileCount = 100;
        int methodsPerFile = 10;
        
        for (int i = 0; i < fileCount; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append("package com.perf;\n");
            sb.append("public class PerfClass").append(i).append(" {\n");
            for (int j = 0; j < methodsPerFile; j++) {
                sb.append("    public void method").append(j).append("() { }\n");
            }
            sb.append("}\n");
            Files.writeString(srcDir.resolve("PerfClass" + i + ".java"), sb.toString());
        }

        Path dbPath = tempDir.resolve("perf.db");
        ParserRegistry registry = new ParserRegistry();
        registry.register(new JavaParser(srcDir));
        CodeIndexer indexer = new CodeIndexer(dbPath.toString(), registry);

        long start = System.currentTimeMillis();
        indexer.index(srcDir.toString());
        long end = System.currentTimeMillis();
        
        long duration = end - start;
        System.out.println("Indexed " + (fileCount * methodsPerFile) + " symbols in " + duration + "ms");
        
        // Assert reasonable performance (adjust based on environment, but 100 files should be < 5s)
        assertTrue(duration < 10000, "Indexing took too long: " + duration + "ms");
    }

    @Test
    void testSearchPerformance() throws Exception {
        Path srcDir = tempDir.resolve("search_perf_src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("SearchTest.java"), "public class SearchTest { public void test() {} }");
        
        Path dbPath = tempDir.resolve("search_perf.db");
        ParserRegistry registry = new ParserRegistry();
        registry.register(new JavaParser(srcDir));
        CodeIndexer indexer = new CodeIndexer(dbPath.toString(), registry);
        indexer.index(srcDir.toString());

        // Warm up
        indexer.search("test");

        long start = System.nanoTime();
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            indexer.search("test");
        }
        long end = System.nanoTime();
        
        long avgDurationNs = (end - start) / iterations;
        double avgDurationMs = avgDurationNs / 1_000_000.0;
        
        System.out.println("Average search time: " + avgDurationMs + "ms");
        
        // Assert search is fast (SQLite should be < 1ms for small datasets)
        assertTrue(avgDurationMs < 5.0, "Search is too slow: " + avgDurationMs + "ms");
    }
}
