# CodeIndex

A lightweight Java tool to index codebases into a local SQLite database for fast symbol lookups, definitions, and references. It features a pluggable architecture supporting multiple programming languages.

## Features
- **Multi-Language Support:** Pluggable architecture currently supporting **Java** (via JavaParser) and **Python** (via Regex).
- **Fast Indexing:** Recursively crawls directories and indexes files based on registered language extensions.
- **Symbol Support:** Indexes Classes, Interfaces, Methods, Fields, and Local Variables.
- **Reference Tracking:** Supported for Java, providing insights into where methods are called or variables are used.
- **SQLite Backend:** Persistent storage with indexed searching.
- **Java 17 Support:** Built for modern Java features.

## Installation

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build from source
```bash
git clone https://github.com/appform-io/codeindex.git
cd codeindex
mvn clean package
```

## CLI Usage

The CLI supports two main commands: `index` and `search`.

### Indexing a Project
To index a project into a database file:
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar index <project_root_path> <sqlite_db_path>
```
*Example:*
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar index /home/user/my-project ./project.db
```
The tool will automatically detect and index supported files (`.java`, `.py`).

### Searching for Symbols
To search for a symbol by name (partial match supported):
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar search <query> <sqlite_db_path>
```
*Example:*
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar search calculateTotal ./project.db
```

#### Class-Aware Search
You can filter symbols by their containing class using the `ClassName::SymbolName` syntax. This is particularly useful when multiple classes have methods with the same name.
*Example:*
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar search OrderService::calculateTotal ./project.db
```

The output will show the symbol kind (CLASS, METHOD, REFERENCE, etc.), the qualified name (`Class::Symbol`), the file path, line number, and signature.


## Library Usage

You can use `CodeIndexer` directly in your Java application. Note that with the new architecture, you need to provide a `ParserRegistry`.

### Dependency
Add the following to your `pom.xml`:
```xml
<dependency>
    <groupId>io.appform.codeindex</groupId>
    <artifactId>codeindex</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Java API Example
```java
import io.appform.codeindex.service.CodeIndexer;
import io.appform.codeindex.parser.ParserRegistry;
import io.appform.codeindex.parser.JavaParser;
import io.appform.codeindex.parser.PythonParser;
import io.appform.codeindex.models.Symbol;
import java.nio.file.Paths;
import java.util.List;

public class MyIndexer {
    public static void main(String[] args) throws Exception {
        String projectPath = "/path/to/source";
        
        // Setup Registry
        ParserRegistry registry = new ParserRegistry();
        registry.register(new JavaParser(Paths.get(projectPath)));
        registry.register(new PythonParser());

        // Initialize Indexer
        CodeIndexer indexer = new CodeIndexer("./my_code.db", registry);
        
        // Index the project
        indexer.index(projectPath);
        
        // Search for symbols
        List<Symbol> results = indexer.search("myFunction");
        results.forEach(s -> System.out.println(s.getName() + " found at " + s.getLine()));
    }
}
```

## License
Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## Development

### Running Tests
```bash
mvn test
```

### Code Coverage
After running tests, coverage reports are generated at:
`target/site/jacoco/index.html`
Currently maintaining ~89% instruction coverage.
