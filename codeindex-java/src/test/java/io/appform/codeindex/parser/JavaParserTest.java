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

package io.appform.codeindex.parser;

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testJavaParsing() throws IOException {
        Path srcDir = tempDir.resolve("io/appform/test");
        Files.createDirectories(srcDir);
        Path javaFile = srcDir.resolve("TestClass.java");
        Files.writeString(javaFile,
                "package io.appform.test;\n" +
                        "\n" +
                        "public class TestClass {\n" +
                        "    private String name;\n" +
                        "    \n" +
                        "    public void hello() {\n" +
                        "        System.out.println(\"Hello \" + name);\n" +
                        "    }\n" +
                        "}\n"
        );

        JavaParser parser = new JavaParser();
        parser.setup(tempDir, List.of());
        List<Symbol> symbols = parser.parse(javaFile, tempDir);

        // Class, Field, Method, Variable(if any), Reference(System.out.println, name)
        assertTrue(symbols.size() >= 3);

        Symbol clazz = symbols.stream()
                .filter(s -> s.getKind() == SymbolKind.CLASS)
                .findFirst()
                .orElseThrow();
        assertEquals("TestClass", clazz.getName());
        assertEquals("io.appform.test", clazz.getPackageName());

        assertTrue(symbols.stream().anyMatch(s -> s.getKind() == SymbolKind.METHOD && s.getName().equals("hello")));
        assertTrue(symbols.stream().anyMatch(s -> s.getKind() == SymbolKind.FIELD && s.getName().equals("name")));
    }

    @Test
    void testExternalLibraryResolution() throws IOException {
        Path srcDir = tempDir.resolve("io/appform/test");
        Files.createDirectories(srcDir);
        Path javaFile = srcDir.resolve("ExternalTest.java");
        Files.writeString(javaFile,
                "package io.appform.test;\n" +
                        "import org.slf4j.Logger;\n" +
                        "import org.slf4j.LoggerFactory;\n" +
                        "\n" +
                        "public class ExternalTest {\n" +
                        "    private static final Logger log = LoggerFactory.getLogger(ExternalTest.class);\n" +
                        "    public void doLog() {\n" +
                        "        log.info(\"Hello\");\n" +
                        "    }\n" +
                        "}\n"
        );

        // Find slf4j-api jar from local maven repo for testing
        String userHome = System.getProperty("user.home");
        Path m2Repo = Paths.get(userHome, ".m2/repository");
        Path slf4jJar = Files.walk(m2Repo)
                .filter(p -> p.toString().contains("slf4j-api") && p.toString().endsWith(".jar"))
                .findFirst()
                .orElse(null);

        JavaParser parser = new JavaParser();
        if (slf4jJar != null) {
            parser.setup(tempDir, List.of(slf4jJar));
        } else {
            parser.setup(tempDir, List.of());
        }
        
        List<Symbol> symbols = parser.parse(javaFile, tempDir);

        // Verify that we can resolve LoggerFactory.getLogger and log.info
        if (slf4jJar != null) {
            boolean foundLoggerRef = symbols.stream()
                    .anyMatch(s -> s.getKind() == SymbolKind.REFERENCE && "org.slf4j.LoggerFactory.getLogger".equals(s.getReferenceTo()));
            boolean foundInfoRef = symbols.stream()
                    .anyMatch(s -> s.getKind() == SymbolKind.REFERENCE && "org.slf4j.Logger.info".equals(s.getReferenceTo()));
            
            assertTrue(foundLoggerRef, "Should have resolved LoggerFactory.getLogger");
            assertTrue(foundInfoRef, "Should have resolved Logger.info");
        }
    }
}
