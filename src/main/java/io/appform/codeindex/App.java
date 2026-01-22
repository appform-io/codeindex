package io.appform.codeindex;

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.service.CodeIndexer;
import lombok.extern.slf4j.Slf4j;

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
        CodeIndexer indexer = new CodeIndexer(dbPath);
        indexer.index(projectPath);
        log.info("Indexing complete!");
    }

    private static void searchIndex(String query, String dbPath) throws Exception {
        CodeIndexer indexer = new CodeIndexer(dbPath);
        List<Symbol> results = indexer.search(query);
        System.out.println("Found " + results.size() + " matches:");
        for (Symbol symbol : results) {
            System.out.printf("[%s] %s -> %s:%d (%s)%n", 
                symbol.getKind(), symbol.getName(), symbol.getFilePath(), symbol.getLine(), symbol.getSignature());
        }
    }
}
