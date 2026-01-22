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
import io.appform.codeindex.parser.PythonParser;
import io.appform.codeindex.parser.ParserRegistry;
import io.appform.codeindex.service.CodeIndexer;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.List;

@Slf4j
public class App {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar codeindex.jar <command> <args...>");
            System.out.println("Commands:");
            System.out.println("  index <project_path> <db_path>");
            System.out.println("  search <query> <db_path>");
            return;
        }

        String command = args[0];
        try {
            if ("index".equals(command)) {
                indexProject(args[1], args[2]);
            } else if ("search".equals(command)) {
                searchIndex(args[1], args[2]);
            } else {
                System.out.println("Unknown command: " + command);
            }
        } catch (Exception e) {
            log.error("Execution failed", e);
        }
    }

    private static void indexProject(String projectPath, String dbPath) throws Exception {
        ParserRegistry registry = new ParserRegistry();
        registry.register(new JavaParser(Paths.get(projectPath)));
        registry.register(new PythonParser());
        CodeIndexer indexer = new CodeIndexer(dbPath, registry);
        indexer.index(projectPath);
        log.info("Indexing complete!");
    }

    private static void searchIndex(String query, String dbPath) throws Exception {
        ParserRegistry registry = new ParserRegistry();
        // Search might not need a source root for JavaParser, but registry needs it if we use it for indexing
        // For search, we just need the indexer.
        CodeIndexer indexer = new CodeIndexer(dbPath, registry);
        List<Symbol> results = indexer.search(query);
        System.out.println("Found " + results.size() + " matches:");
        for (Symbol symbol : results) {
            String displayName = symbol.getClassName() != null 
                ? symbol.getClassName() + "::" + symbol.getName() 
                : symbol.getName();
            System.out.printf("[%s] %s -> %s:%d (%s)%n", 
                symbol.getKind(), displayName, symbol.getFilePath(), symbol.getLine(), symbol.getSignature());
        }
    }
}
