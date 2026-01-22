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

class PythonParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testPythonParsing() throws IOException {
        Path pyFile = tempDir.resolve("script.py");
        Files.writeString(pyFile,
                "class MyClass:\n" +
                        "    def __init__(self):\n" +
                        "        pass\n" +
                        "    def my_method(self, x):\n" +
                        "        return x\n" +
                        "\n" +
                        "def top_level_func():\n" +
                        "    pass\n"
        );

        PythonParser parser = new PythonParser();
        List<Symbol> symbols = parser.parse(pyFile, tempDir);

        assertEquals(4, symbols.size());

        Symbol clazz = symbols.stream().filter(s -> s.getKind() == SymbolKind.CLASS).findFirst().orElseThrow();
        assertEquals("MyClass", clazz.getName());
        assertEquals(1, clazz.getLine());

        List<Symbol> methods = symbols.stream().filter(s -> s.getKind() == SymbolKind.METHOD).toList();
        assertEquals(3, methods.size());
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("__init__")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("my_method")));
        assertTrue(methods.stream().anyMatch(m -> m.getName().equals("top_level_func")));
    }
}
