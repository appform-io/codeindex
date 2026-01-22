# Guidelines for Coding Agents

This document defines the strict standards and procedures that all AI coding agents must follow when contributing to the **CodeIndex** project.

## 1. Code Style and Enforcement

Consistency is paramount. We follow a specific style derived from the `sentinel-ai` project.

- **Checkstyle:** All changes must pass `mvn checkstyle:check`. This is enforced during the `validate` phase of the build.
- **Indentation:** 4 spaces for blocks, 8 spaces for line continuations (e.g., builder chains, ternary operators).
- **Local Variables:** Use `final var` for all local variables where the type is clear from the assignment.
- **Lombok:** Use Lombok annotations (`@Builder`, `@Value`, `@Data`, `@Slf4j`) to reduce boilerplate, but ensure `lombok.config` is respected for test coverage exclusion.
- **Line Separators:** Package declarations must be preceded by a blank line after the license header.

## 2. Licensing

- **License Header:** Every new source file must include the Apache License 2.0 header.
- **Attribution:** Do not modify the existing copyright notice year or contributors list unless explicitly directed.

## 3. Security

- **Secrets:** Never commit secrets, API keys, or credentials. Use `.env` files (ignored by git) for local testing.
- **Input Validation:** All file paths and user queries must be treated as untrusted. Ensure safe path resolution to prevent directory traversal.
- **SQL Safety:** Use `PreparedStatement` with parameterized queries for all SQLite operations to prevent SQL injection.

## 4. Performance

- **Efficiency:** CodeIndex is intended for large codebases. Avoid O(N^2) algorithms where O(N log N) or O(N) is possible.
- **Memory Management:** Be mindful of memory usage during deep crawling or large-scale indexing. Use streaming where applicable.
- **Database Limits:** Always implement and respect limits for search queries (default is 1000) to prevent OOM errors in the CLI or exporters.

## 5. Contribution Workflow

1.  **Analyze:** Use `grep` and `glob` to understand existing patterns before proposing changes.
2.  **Plan:** State a clear, concise plan and wait for user approval if the change is significant.
3.  **Verify:** 
    *   Run `mvn test` to ensure no regressions.
    *   Run `mvn checkstyle:check` to ensure style compliance.
    *   Run `mvn jacoco:report` to verify that coverage has not significantly decreased (target: >90%).
4.  **Commit:** Use descriptive, imperative-style commit messages (e.g., "feat: add...", "fix: resolve...").

Failure to adhere to these guidelines will result in the rejection of the contribution.
