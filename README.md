# CodeIndex

A lightweight Java tool to index codebases into a local SQLite database for fast symbol lookups, definitions, and references. It features a pluggable architecture supporting multiple programming languages.

## Features
- **Multi-Language Support:** Pluggable architecture supporting **Java** (via JavaParser) and **Python** (via ANTLR4).
- **Discovery System:** Automatically finds and registers language parsers using the Reflections API.
- **Fast Indexing:** Recursively crawls directories and indexes files based on registered language extensions.
- **Symbol Support:** Indexes Classes, Interfaces, Methods, Fields, and Local Variables.
- **Reference Tracking:** Supported for Java, providing insights into where methods are called or variables are used.
- **Polished CLI:** GNU-style command line interface using `picocli`.
- **SQLite Backend:** Persistent storage with indexed searching.
- **CI/CD Integrated:** Automated builds and aggregate coverage reporting via GitHub Actions.

## Installation

### Prerequisites
- Java 17 or higher
- Maven 3.8+

### Build from source
```bash
git clone https://github.com/appform-io/codeindex.git
cd codeindex
mvn clean install -PskipCheckstyle
```

## CLI Usage

The CLI supports three main subcommands: `index`, `search`, and `export`.

### Indexing a Project
To index a project into a database file:
```bash
java -jar codeindex-cli/target/codeindex-cli-1.0-SNAPSHOT.jar index <project_root_path> <sqlite_db_path>
```
*Example:*
```bash
java -jar codeindex-cli/target/codeindex-cli-1.0-SNAPSHOT.jar index /home/user/my-project ./project.db
```
The tool will automatically detect and index supported files (`.java`, `.py`).

### Searching for Symbols
To search for a symbol by name (regex supported):
```bash
java -jar codeindex-cli/target/codeindex-cli-1.0-SNAPSHOT.jar search <query> <sqlite_db_path>
```
*Example:*
```bash
java -jar codeindex-cli/target/codeindex-cli-1.0-SNAPSHOT.jar search ".*Service" ./project.db
```

#### Class-Aware Search
You can filter symbols by their containing class using the `ClassName::SymbolName` syntax.
*Example:*
```bash
java -jar codeindex-cli/target/codeindex-cli-1.0-SNAPSHOT.jar search OrderService::calculateTotal ./project.db
```

### Exporting Symbol Index
Export indexed symbols to Markdown or XML formats.

```bash
java -jar codeindex-cli/target/codeindex-cli-1.0-SNAPSHOT.jar export <db_path> <output_file> [OPTIONS]
```

**Options:**
- `-f`, `--format`: `markdown` (default) or `xml`.
- `-k`, `--kinds`: Comma-separated list of `SymbolKind` (e.g., `CLASS,METHOD`). Defaults to all.

*Example:*
```bash
java -jar codeindex-cli/target/codeindex-cli-1.0-SNAPSHOT.jar export ./project.db ./summary.md --format markdown --kinds CLASS,METHOD
```

The output will group symbols by file and class for better organization.

## Library Usage

### Dependency
Add the following to your `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.appform.codeindex</groupId>
            <artifactId>codeindex-bom</artifactId>
            <version>1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.appform.codeindex</groupId>
        <artifactId>codeindex-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.appform.codeindex</groupId>
        <artifactId>codeindex-java</artifactId>
    </dependency>
    <dependency>
        <groupId>io.appform.codeindex</groupId>
        <artifactId>codeindex-python</artifactId>
    </dependency>
</dependencies>
```

### Java API Example
```java
import io.appform.codeindex.service.CodeIndexer;
import io.appform.codeindex.parser.ParserRegistry;
import io.appform.codeindex.models.Symbol;
import java.util.List;

public class MyIndexer {
    public static void main(String[] args) throws Exception {
        // Registry automatically discovers @DiscoverableParser implementations
        ParserRegistry registry = new ParserRegistry();
        CodeIndexer indexer = new CodeIndexer("./my_code.db", registry);
        
        indexer.index("/path/to/source");
        
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
mvn test -PskipCheckstyle
```

### Code Coverage
After running `mvn install`, a consolidated coverage report is generated at:
`codeindex-reports/target/site/jacoco-aggregate/index.html`
Currently maintaining high instruction coverage across core logic and parsers.
