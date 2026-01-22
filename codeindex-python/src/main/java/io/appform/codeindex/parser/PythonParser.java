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
import io.appform.codeindex.parser.annotation.DiscoverableParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@DiscoverableParser
public class PythonParser implements Parser {

    private static final Pattern CLASS_PATTERN = Pattern.compile("^class\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern FUNC_PATTERN = Pattern.compile("^\\s*def\\s+([a-zA-Z_][a-zA-Z0-9_]*)");

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("py");
    }

    @Override
    public List<Symbol> parse(Path path, Path sourceRoot) {
        final var symbols = new ArrayList<Symbol>();
        try {
            final var lines = Files.readAllLines(path);
            final var filePath = sourceRoot != null
                    ? sourceRoot.toAbsolutePath().relativize(path.toAbsolutePath()).toString()
                    : path.toAbsolutePath().toString();

            for (int i = 0; i < lines.size(); i++) {
                final var line = lines.get(i);
                final var classMatcher = CLASS_PATTERN.matcher(line);
                if (classMatcher.find()) {
                    symbols.add(Symbol.builder()
                            .name(classMatcher.group(1))
                            .kind(SymbolKind.CLASS)
                            .filePath(filePath)
                            .line(i + 1)
                            .signature(line.trim())
                            .build());
                    continue;
                }

                final var funcMatcher = FUNC_PATTERN.matcher(line);
                if (funcMatcher.find()) {
                    symbols.add(Symbol.builder()
                            .name(funcMatcher.group(1))
                            .kind(SymbolKind.METHOD)
                            .filePath(filePath)
                            .line(i + 1)
                            .signature(line.trim())
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("Error parsing python file: {}", path, e);
        }
        return symbols;
    }
}
