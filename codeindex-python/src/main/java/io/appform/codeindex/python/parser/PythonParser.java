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

package io.appform.codeindex.python.parser;

import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import io.appform.codeindex.parser.Parser;
import io.appform.codeindex.parser.annotation.DiscoverableParser;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@DiscoverableParser
public class PythonParser implements Parser {

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("py");
    }

    @Override
    public List<Symbol> parse(Path path, Path sourceRoot) {
        final var symbols = new ArrayList<Symbol>();
        final var filePath = sourceRoot != null
                ? sourceRoot.toAbsolutePath().relativize(path.toAbsolutePath()).toString()
                : path.toAbsolutePath().toString();
        try {
            var charStream = CharStreams.fromPath(path);
            var lexer = new Python3Lexer(charStream);
            var tokens = new CommonTokenStream(lexer);
            var parser = new Python3Parser(tokens);

            parser.removeErrorListeners();

            ParseTree tree = parser.file_input();
            var visitor = new PythonVisitor(symbols, filePath);
            visitor.visit(tree);
        }
        catch (IOException e) {
            log.error("Error parsing python file: {}", path, e);
        }
        return symbols;
    }

    private static class PythonVisitor extends Python3ParserBaseVisitor<Void> {
        private final List<Symbol> symbols;
        private final String filePath;

        public PythonVisitor(List<Symbol> symbols, String filePath) {
            this.symbols = symbols;
            this.filePath = filePath;
        }

        @Override
        public Void visitClassdef(Python3Parser.ClassdefContext ctx) {
            if (ctx.name() != null) {
                symbols.add(Symbol.builder()
                        .name(ctx.name().getText())
                        .kind(SymbolKind.CLASS)
                        .filePath(filePath)
                        .line(ctx.getStart().getLine())
                        .signature(ctx.getText().split("\n")[0])
                        .build());
            }
            return super.visitClassdef(ctx);
        }

        @Override
        public Void visitFuncdef(Python3Parser.FuncdefContext ctx) {
            if (ctx.name() != null) {
                symbols.add(Symbol.builder()
                        .name(ctx.name().getText())
                        .kind(SymbolKind.METHOD)
                        .filePath(filePath)
                        .line(ctx.getStart().getLine())
                        .signature(ctx.getText().split("\n")[0])
                        .build());
            }
            return super.visitFuncdef(ctx);
        }
    }
}
