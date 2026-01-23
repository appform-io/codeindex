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
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface Parser {
    Set<String> supportedExtensions();

    default void setup(Path sourceRoot, List<Path> classpath) {
        // Default implementation does nothing
    }

    List<Symbol> parse(Path path, Path sourceRoot);
}
