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

import io.appform.codeindex.models.SearchRequest;
import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import io.appform.codeindex.parser.ParserRegistry;
import io.appform.codeindex.service.CodeIndexer;
import io.appform.codeindex.service.CodeExporter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Slf4j
@Command(name = "codeindex", mixinStandardHelpOptions = true, version = "1.0",
        description = "Indexes codebases into a SQLite database and provides search/export capabilities.")
public class App implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "index", description = "Index a project directory")
    static class IndexCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the project to index")
        private String projectPath;

        @Parameters(index = "1", description = "Path to the SQLite database file")
        private String dbPath;

        @Option(names = {"-cp", "--classpath"}, description = "Comma-separated list of jar files or directories for type resolution", split = ",")
        private List<String> classpath;

        @Override
        public Integer call() throws Exception {
            final var registry = new ParserRegistry();
            final var indexer = new CodeIndexer(dbPath, registry);
            final var cpPaths = classpath != null
                    ? classpath.stream().map(Paths::get).collect(Collectors.toList())
                    : List.<Path>of();
            indexer.index(projectPath, cpPaths);
            log.info("Indexing complete!");
            return 0;
        }
    }

    @Command(name = "search", description = "Search the index for symbols")
    static class SearchCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the SQLite database file")
        private String dbPath;

        @Option(names = {"-q", "--query"}, description = "Search query (regex/like supported)")
        private String query;

        @Option(names = {"-k", "--kinds"}, description = "Comma-separated list of symbol kinds to filter (e.g. CLASS,METHOD)", split = ",")
        private Set<SymbolKind> kinds;

        @Option(names = {"-f", "--file-path"}, description = "Filter by file path glob pattern (e.g. **/src/*.java)")
        private String filePathGlob;

        @Option(names = {"-c", "--class"}, description = "Filter by class name")
        private String className;

        @Option(names = {"-p", "--package"}, description = "Filter by package name")
        private String packageName;

        @Option(names = {"-l", "--limit"}, description = "Limit the number of results", defaultValue = "1000")
        private int limit;

        @Override
        public Integer call() throws Exception {
            final var registry = new ParserRegistry();
            final var indexer = new CodeIndexer(dbPath, registry);
            final var request = SearchRequest.builder()
                    .query(query)
                    .kinds(kinds)
                    .filePathGlob(filePathGlob)
                    .className(className)
                    .packageName(packageName)
                    .limit(limit)
                    .build();
            final var results = indexer.search(request);
            System.out.println("Found " + results.size() + " matches:");
            for (Symbol symbol : results) {
                final var displayName = symbol.getClassName() != null
                        ? symbol.getClassName() + "::" + symbol.getName()
                        : symbol.getName();
                System.out.printf("[%s] %s -> %s:%d (%s)%n",
                        symbol.getKind(), displayName, symbol.getFilePath(), symbol.getLine(), symbol.getSignature());
            }
            return 0;
        }
    }

    @Command(name = "export", description = "Export indexed symbols to a file")
    static class ExportCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the SQLite database file")
        private String dbPath;

        @Parameters(index = "1", description = "Output file path")
        private String outputFile;

        @Option(names = {"-f", "--format"}, description = "Output format: markdown, xml", defaultValue = "markdown")
        private String format;

        @Option(names = {"-k", "--kinds"}, description = "Comma-separated list of symbol kinds to export (e.g. CLASS,METHOD)", split = ",")
        private Set<SymbolKind> kinds;

        @Override
        public Integer call() throws Exception {
            final var exporter = new CodeExporter(dbPath);
            exporter.export(outputFile, format, kinds);
            log.info("Export complete: {}", outputFile);
            return 0;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App())
                .addSubcommand(new IndexCommand())
                .addSubcommand(new SearchCommand())
                .addSubcommand(new ExportCommand())
                .setExecutionStrategy(new CommandLine.RunLast())
                .execute(args);
        // We don't want to call System.exit(exitCode) during unit tests if they call main directly
        // But for CLI it's expected. We can check a property or just return if exitCode is 0 and it's a test.
        // Actually, picocli handles execution. Let's just use the exit code if not in test.
    }

}
