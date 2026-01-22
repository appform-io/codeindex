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

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import io.appform.codeindex.storage.SQLiteStorage;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CodeExporter {
    private final String dbPath;

    public CodeExporter(String dbPath) {
        this.dbPath = dbPath;
    }

    public void export(String outputFile, String format, Set<SymbolKind> kinds) throws SQLException, IOException {
        try (SQLiteStorage storage = new SQLiteStorage(dbPath)) {
            final var symbols = storage.getAllSymbols(kinds);
            if ("xml".equalsIgnoreCase(format)) {
                exportToXml(symbols, outputFile);
            } else {
                exportToMarkdown(symbols, outputFile);
            }
        }
    }

    private void exportToMarkdown(List<Symbol> symbols, String outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("# Project Symbol Index");
            writer.println();

            final var groupedByFile = symbols.stream()
                    .collect(Collectors.groupingBy(Symbol::getFilePath));

            for (Map.Entry<String, List<Symbol>> fileEntry : groupedByFile.entrySet()) {
                writer.println("## File: " + fileEntry.getKey());
                writer.println();
                
                // Group by Class within File
                final var groupedByClass = fileEntry.getValue().stream()
                        .collect(Collectors.groupingBy(s -> s.getClassName() != null ? s.getClassName() : "Top-level"));

                for (Map.Entry<String, List<Symbol>> classEntry : groupedByClass.entrySet()) {
                    if (!"Top-level".equals(classEntry.getKey())) {
                        writer.println("### Class: " + classEntry.getKey());
                    }
                    writer.println("| Kind | Name | Line | Signature |");
                    writer.println("|------|------|------|-----------|");
                    for (Symbol symbol : classEntry.getValue()) {
                        writer.printf("| %s | %s | %d | `%s` |%n",
                                symbol.getKind(),
                                symbol.getName(),
                                symbol.getLine(),
                                symbol.getSignature().replace("|", "\\|"));
                    }
                    writer.println();
                }
            }
        }
    }

    private void exportToXml(List<Symbol> symbols, String outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<project>");

            final var groupedByFile = symbols.stream()
                    .collect(Collectors.groupingBy(Symbol::getFilePath));

            for (Map.Entry<String, List<Symbol>> fileEntry : groupedByFile.entrySet()) {
                writer.println("  <file path=\"" + escapeXml(fileEntry.getKey()) + "\">");

                final var groupedByClass = fileEntry.getValue().stream()
                        .collect(Collectors.groupingBy(s -> s.getClassName() != null ? s.getClassName() : "Top-level"));

                for (Map.Entry<String, List<Symbol>> classEntry : groupedByClass.entrySet()) {
                    if (!"Top-level".equals(classEntry.getKey())) {
                        writer.println("    <class name=\"" + escapeXml(classEntry.getKey()) + "\">");
                        for (Symbol symbol : classEntry.getValue()) {
                            writeSymbolXml(writer, symbol, "      ");
                        }
                        writer.println("    </class>");
                    } else {
                        for (Symbol symbol : classEntry.getValue()) {
                            writeSymbolXml(writer, symbol, "    ");
                        }
                    }
                }
                writer.println("  </file>");
            }
            writer.println("</project>");
        }
    }

    private void writeSymbolXml(PrintWriter writer, Symbol symbol, String indent) {
        writer.printf("%s<symbol kind=\"%s\" name=\"%s\" line=\"%d\" signature=\"%s\"/>%n",
                indent,
                symbol.getKind(),
                escapeXml(symbol.getName()),
                symbol.getLine(),
                escapeXml(symbol.getSignature()));
    }

    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
