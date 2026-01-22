package io.appform.codeindex.models;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Symbol {
    String name;
    SymbolKind kind;
    String filePath;
    int line;
    String signature;
    String referenceTo;
}
