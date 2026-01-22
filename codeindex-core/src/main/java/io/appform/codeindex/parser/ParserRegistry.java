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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ParserRegistry {
    private final List<Parser> parsers = new ArrayList<>();

    public void register(Parser parser) {
        parsers.add(parser);
    }

    public Parser getParserForFile(Path path) {
        final var fileName = path.getFileName().toString();
        final var lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return null;
        }
        final var extension = fileName.substring(lastDotIndex + 1);
        return parsers.stream()
                .filter(p -> p.supportedExtensions().contains(extension))
                .findFirst()
                .orElse(null);
    }

    public Set<String> getSupportedExtensions() {
        return parsers.stream()
                .flatMap(p -> p.supportedExtensions().stream())
                .collect(Collectors.toSet());
    }
}
