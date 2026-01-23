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

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.appform.codeindex.models.Symbol;
import io.appform.codeindex.models.SymbolKind;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@io.appform.codeindex.parser.annotation.DiscoverableParser
public class JavaParser implements Parser {

    private CombinedTypeSolver typeSolver;

    public JavaParser() {
        this(null);
    }

    public JavaParser(Path sourceRoot) {
        this.typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        if (sourceRoot != null) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        }
        final var symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    static {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("java");
    }

    @Override
    public List<Symbol> parse(Path path, Path sourceRoot) {
        final var symbols = new ArrayList<Symbol>();
        try {
            final var cu = StaticJavaParser.parse(path);
            final var packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getName().asString())
                    .orElse(null);
            final var filePath = sourceRoot != null
                    ? sourceRoot.relativize(path).toString()
                    : path.toString();

            // Classes/Interfaces
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
                final var className = cid.getNameAsString();
                symbols.add(Symbol.builder()
                        .name(className)
                        .className(className)
                        .packageName(packageName)
                        .kind(SymbolKind.CLASS)
                        .filePath(filePath)
                        .line(cid.getBegin().map(p -> p.line).orElse(-1))
                        .signature(cid.getNameAsString())
                        .build());
            });

            // Methods
            cu.findAll(MethodDeclaration.class).forEach(md -> {
                final var className = md.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                symbols.add(Symbol.builder()
                        .name(md.getNameAsString())
                        .className(className)
                        .packageName(packageName)
                        .kind(SymbolKind.METHOD)
                        .filePath(filePath)
                        .line(md.getBegin().map(p -> p.line).orElse(-1))
                        .signature(md.getSignature().asString())
                        .build());
            });

            // Fields
            cu.findAll(FieldDeclaration.class).forEach(fd -> {
                final var className = fd.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                fd.getVariables().forEach(v -> {
                    symbols.add(Symbol.builder()
                            .name(v.getNameAsString())
                            .className(className)
                            .packageName(packageName)
                            .kind(SymbolKind.FIELD)
                            .filePath(filePath)
                            .line(v.getBegin().map(p -> p.line).orElse(-1))
                            .signature(v.getTypeAsString() + " " + v.getNameAsString())
                            .build());
                });
            });

            // Local Variables
            cu.findAll(VariableDeclarator.class).forEach(vd -> {
                // Filter out fields (they are handled above)
                if (vd.getParentNode().map(p -> p instanceof FieldDeclaration).orElse(false)) {
                    return;
                }
                final var className = vd.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                symbols.add(Symbol.builder()
                        .name(vd.getNameAsString())
                        .className(className)
                        .packageName(packageName)
                        .kind(SymbolKind.VARIABLE)
                        .filePath(filePath)
                        .line(vd.getBegin().map(p -> p.line).orElse(-1))
                        .signature(vd.getTypeAsString() + " " + vd.getNameAsString())
                        .build());
            });

            // Method Calls (References)
            cu.findAll(MethodCallExpr.class).forEach(mce -> {
                final var className = mce.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                try {
                    final ResolvedMethodDeclaration resolved = mce.resolve();
                    symbols.add(Symbol.builder()
                            .name(mce.getNameAsString())
                            .className(className)
                            .packageName(packageName)
                            .kind(SymbolKind.REFERENCE)
                            .filePath(filePath)
                            .line(mce.getBegin().map(p -> p.line).orElse(-1))
                            .signature(mce.toString())
                            .referenceTo(resolved.getQualifiedName())
                            .build());
                }
                catch (Exception e) {
                    log.debug("Could not resolve method call: {}", mce.getNameAsString());
                }
            });

            // Name Expressions (Variable References)
            cu.findAll(NameExpr.class).forEach(ne -> {
                final var className = ne.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                try {
                    final ResolvedValueDeclaration resolved = ne.resolve();
                    if (resolved.isVariable() || resolved.isField() || resolved.isEnumConstant()) {
                        symbols.add(Symbol.builder()
                                .name(ne.getNameAsString())
                                .className(className)
                                .packageName(packageName)
                                .kind(SymbolKind.REFERENCE)
                                .filePath(filePath)
                                .line(ne.getBegin().map(p -> p.line).orElse(-1))
                                .signature(ne.getNameAsString())
                                .referenceTo(resolved.getName())
                                .build());
                    }
                }
                catch (Exception e) {
                    log.debug("Could not resolve name expression: {}", ne.getNameAsString());
                }
            });

        }
        catch (Exception e) {
            log.error("Failed to parse file: {}", path, e);
        }
        return symbols;
    }
}
