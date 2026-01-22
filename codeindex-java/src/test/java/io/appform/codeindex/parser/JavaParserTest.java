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
import java.util.List;

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

        JavaParser parser = new JavaParser(tempDir);
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
}
