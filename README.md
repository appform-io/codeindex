# CodeIndex

A lightweight Java tool to index codebases into a local SQLite database for fast symbol lookups, definitions, and references. It can be used as a CLI tool or integrated as a library into other Java projects.

## Features
- **Fast Indexing:** Recursively crawls directories and parses Java files using JavaParser.
- **Symbol Support:** Indexes Classes, Interfaces, Methods, Fields, and Local Variables.
- **Reference Tracking:** Uses symbol resolution to find and store where methods are called or variables are used.
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
To index a Java project into a database file:
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar index <project_root_path> <sqlite_db_path>
```
*Example:*
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar index /home/user/my-project ./project.db
```

### Searching for Symbols
To search for a symbol by name (partial match supported):
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar search <query> <sqlite_db_path>
```
*Example:*
```bash
java -jar target/codeindex-1.0-SNAPSHOT.jar search calculateTotal ./project.db
```

The output will show the symbol kind (CLASS, METHOD, REFERENCE, etc.), the file path, line number, and signature.

## Library Usage

You can also use `CodeIndexer` directly in your Java application.

### Dependency
Add the following to your `pom.xml` (after installing to local repo or hosting):
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
import io.appform.codeindex.models.Symbol;

public class MyIndexer {
    public static void main(String[] args) throws Exception {
        CodeIndexer indexer = new CodeIndexer("./my_code.db");
        
        // Index a project
        indexer.index("/path/to/java/source");
        
        // Search for symbols
        List<Symbol> results = indexer.search("myFunction");
        results.forEach(s -> System.out.println(s.getName() + " found at " + s.getLine()));
    }
}
```

## Development

### Running Tests
```bash
mvn test
```

### Code Coverage
After running tests, coverage reports are generated at:
`target/site/jacoco/index.html`
