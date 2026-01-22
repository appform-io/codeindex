package io.appform.codeindex.service;

import io.appform.codeindex.crawler.FileCrawler;
import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.parser.CodeParser;
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

    public CodeIndexer(String dbPath) {
        this.dbPath = dbPath;
        this.crawler = new FileCrawler();
    }

    public void index(String projectPath) throws Exception {
        log.info("Starting indexing for project: {}", projectPath);
        List<Path> files = crawler.crawl(projectPath);
        CodeParser parser = new CodeParser(Paths.get(projectPath));
        
        try (SQLiteStorage storage = new SQLiteStorage(dbPath)) {
            for (Path file : files) {
                try {
                    List<Symbol> symbols = parser.parse(file, Paths.get(projectPath));
                    storage.saveSymbols(symbols);
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
