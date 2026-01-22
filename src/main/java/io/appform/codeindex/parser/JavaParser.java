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
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
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
public class JavaParser implements Parser {

    private final CombinedTypeSolver typeSolver;

    public JavaParser(Path sourceRoot) {
        this.typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        if (sourceRoot != null) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        }
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
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
        List<Symbol> symbols = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            String filePath = sourceRoot != null 
                ? sourceRoot.toAbsolutePath().relativize(path.toAbsolutePath()).toString()
                : path.toAbsolutePath().toString();

            // Classes/Interfaces
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
                String className = cid.getNameAsString();
                symbols.add(Symbol.builder()
                        .name(className)
                        .className(className)
                        .kind(cid.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS)
                        .filePath(filePath)
                        .line(cid.getBegin().map(p -> p.line).orElse(-1))
                        .signature(cid.getFullyQualifiedName().orElse(cid.getNameAsString()))
                        .build());
            });

            // Methods
            cu.findAll(MethodDeclaration.class).forEach(md -> {
                String className = md.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                symbols.add(Symbol.builder()
                        .name(md.getNameAsString())
                        .className(className)
                        .kind(SymbolKind.METHOD)
                        .filePath(filePath)
                        .line(md.getBegin().map(p -> p.line).orElse(-1))
                        .signature(md.getDeclarationAsString())
                        .build());
            });

            // Fields
            cu.findAll(FieldDeclaration.class).forEach(fd -> {
                String className = fd.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                fd.getVariables().forEach(v -> {
                    symbols.add(Symbol.builder()
                            .name(v.getNameAsString())
                            .className(className)
                            .kind(SymbolKind.FIELD)
                            .filePath(filePath)
                            .line(v.getBegin().map(p -> p.line).orElse(-1))
                            .signature(fd.toString().trim())
                            .build());
                });
            });

            // Variables (local variables in methods)
            cu.findAll(VariableDeclarator.class).forEach(vd -> {
                // If it's already indexed as a field, don't re-index it as a variable
                if (vd.getParentNode().map(p -> p instanceof FieldDeclaration).orElse(false)) {
                    return;
                }
                String className = vd.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                symbols.add(Symbol.builder()
                        .name(vd.getNameAsString())
                        .className(className)
                        .kind(SymbolKind.VARIABLE)
                        .filePath(filePath)
                        .line(vd.getBegin().map(p -> p.line).orElse(-1))
                        .signature(vd.toString().trim())
                        .build());
            });

            // Method Calls (References)
            cu.findAll(MethodCallExpr.class).forEach(mce -> {
                String className = mce.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                try {
                    mce.resolve().toAst().ifPresent(declaration -> {
                        symbols.add(Symbol.builder()
                                .name(mce.getNameAsString())
                                .className(className)
                                .kind(SymbolKind.REFERENCE)
                                .filePath(filePath)
                                .line(mce.getBegin().map(p -> p.line).orElse(-1))
                                .signature(mce.toString())
                                .referenceTo(declaration.toString().substring(0, Math.min(declaration.toString().length(), 100))) // Simplified for now
                                .build());
                    });
                } catch (Exception e) {
                    log.debug("Could not resolve method call: {}", mce);
                }
            });

            // Name Expressions (Variable References)
            cu.findAll(NameExpr.class).forEach(ne -> {
                String className = ne.findAncestor(ClassOrInterfaceDeclaration.class)
                        .map(ClassOrInterfaceDeclaration::getNameAsString)
                        .orElse(null);
                try {
                    ne.resolve().toAst().ifPresent(declaration -> {
                        symbols.add(Symbol.builder()
                                .name(ne.getNameAsString())
                                .className(className)
                                .kind(SymbolKind.REFERENCE)
                                .filePath(filePath)
                                .line(ne.getBegin().map(p -> p.line).orElse(-1))
                                .signature(ne.toString())
                                .referenceTo(declaration.toString().substring(0, Math.min(declaration.toString().length(), 100)))
                                .build());
                    });
                } catch (Exception e) {
                    log.debug("Could not resolve name expression: {}", ne);
                }
            });

        } catch (IOException e) {
            log.error("Error parsing file: {}", path, e);
        }
        return symbols;
    }
}
