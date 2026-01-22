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

import io.appform.codeindex.crawler.FileCrawler;
import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.parser.Parser;
import io.appform.codeindex.parser.ParserRegistry;
import io.appform.codeindex.storage.SQLiteStorage;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

@Slf4j
public class CodeIndexer {
    private final String dbPath;
    private final FileCrawler crawler;
    private final ParserRegistry parserRegistry;

    public CodeIndexer(String dbPath, ParserRegistry parserRegistry) {
        this.dbPath = dbPath;
        this.parserRegistry = parserRegistry;
        this.crawler = new FileCrawler();
    }

    public void index(String projectPath) throws Exception {
        log.info("Starting indexing for project: {}", projectPath);
        final var files = crawler.crawl(projectPath, parserRegistry.getSupportedExtensions());
        
        try (SQLiteStorage storage = new SQLiteStorage(dbPath)) {
            for (Path file : files) {
                try {
                    final var parser = parserRegistry.getParserForFile(file);
                    if (parser != null) {
                        final var symbols = parser.parse(file, Paths.get(projectPath));
                        storage.saveSymbols(symbols);
                    }
                } catch (Exception e) {
                    log.error("Failed to index file: {}", file, e);
                }
            }
        }
        log.info("Indexing completed for project: {}", projectPath);
    }

    public List<Symbol> search(String query) throws SQLException {
        try (SQLiteStorage storage = new SQLiteStorage(dbPath)) {
            return storage.search(query);
        }
    }
}
