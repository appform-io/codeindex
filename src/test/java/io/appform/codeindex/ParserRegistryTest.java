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

import io.appform.codeindex.crawler.FileCrawler;
import io.appform.codeindex.parser.Parser;
import io.appform.codeindex.parser.ParserRegistry;
import io.appform.codeindex.models.Symbol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParserRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void testRegistryDispatch() {
        ParserRegistry registry = new ParserRegistry();
        Parser mockJavaParser = mock(Parser.class);
        when(mockJavaParser.supportedExtensions()).thenReturn(Set.of("java"));
        
        Parser mockPyParser = mock(Parser.class);
        when(mockPyParser.supportedExtensions()).thenReturn(Set.of("py"));

        registry.register(mockJavaParser);
        registry.register(mockPyParser);

        assertEquals(mockJavaParser, registry.getParserForFile(Path.of("Test.java")));
        assertEquals(mockPyParser, registry.getParserForFile(Path.of("script.py")));
        assertNull(registry.getParserForFile(Path.of("README.md")));
        assertNull(registry.getParserForFile(Path.of("Makefile"))); // No dot
        
        assertEquals(Set.of("java", "py"), registry.getSupportedExtensions());
    }

    @Test
    void testFileCrawlerWithMultipleExtensions() throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.createFile(src.resolve("A.java"));
        Files.createFile(src.resolve("B.py"));
        Files.createFile(src.resolve("C.txt"));
        Files.createFile(src.resolve("D")); // No extension

        FileCrawler crawler = new FileCrawler();
        List<Path> files = crawler.crawl(src.toString(), Set.of("java", "py"));

        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(p -> p.toString().endsWith("A.java")));
        assertTrue(files.stream().anyMatch(p -> p.toString().endsWith("B.py")));
        assertFalse(files.stream().anyMatch(p -> p.toString().endsWith("C.txt")));
        assertFalse(files.stream().anyMatch(p -> p.toString().endsWith("D")));
    }
}
