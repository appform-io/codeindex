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

@Slf4j
public class CodeParser {

    private final CombinedTypeSolver typeSolver;

    public CodeParser(Path sourceRoot) {
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

    public List<Symbol> parse(Path path, Path sourceRoot) {
        List<Symbol> symbols = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            String filePath = sourceRoot != null 
                ? sourceRoot.toAbsolutePath().relativize(path.toAbsolutePath()).toString()
                : path.toAbsolutePath().toString();

            // Classes/Interfaces
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
                symbols.add(Symbol.builder()
                        .name(cid.getNameAsString())
                        .kind(cid.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS)
                        .filePath(filePath)
                        .line(cid.getBegin().map(p -> p.line).orElse(-1))
                        .signature(cid.getFullyQualifiedName().orElse(cid.getNameAsString()))
                        .build());
            });

            // Methods
            cu.findAll(MethodDeclaration.class).forEach(md -> {
                symbols.add(Symbol.builder()
                        .name(md.getNameAsString())
                        .kind(SymbolKind.METHOD)
                        .filePath(filePath)
                        .line(md.getBegin().map(p -> p.line).orElse(-1))
                        .signature(md.getDeclarationAsString())
                        .build());
            });

            // Fields
            cu.findAll(FieldDeclaration.class).forEach(fd -> {
                fd.getVariables().forEach(v -> {
                    symbols.add(Symbol.builder()
                            .name(v.getNameAsString())
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
                symbols.add(Symbol.builder()
                        .name(vd.getNameAsString())
                        .kind(SymbolKind.VARIABLE)
                        .filePath(filePath)
                        .line(vd.getBegin().map(p -> p.line).orElse(-1))
                        .signature(vd.toString().trim())
                        .build());
            });

            // Method Calls (References)
            cu.findAll(MethodCallExpr.class).forEach(mce -> {
                try {
                    mce.resolve().toAst().ifPresent(declaration -> {
                        symbols.add(Symbol.builder()
                                .name(mce.getNameAsString())
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
                try {
                    ne.resolve().toAst().ifPresent(declaration -> {
                        symbols.add(Symbol.builder()
                                .name(ne.getNameAsString())
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
