---
description: 'Guidelines for building Java base applications'
applyTo: '**/*.java'
---

# Java Development

## General Instructions

- Address code smells proactively during development rather than accumulating technical debt.
- Focus on readability, maintainability, and performance when refactoring identified issues.
- Use IDE / Code editor reported warnings and suggestions to catch common patterns early in development.

## Best practices

- **Records**: For classes primarily intended to store data (e.g., DTOs, immutable data structures), **Java Records
  should be used instead of traditional classes**.
- **Pattern Matching**: Utilize pattern matching for `instanceof` and `switch` expression to simplify conditional logic
  and type casting.
- **Type Inference**: Use `var` for local variable declarations to improve readability, but only when the type is
  explicitly clear from the right-hand side of the expression.
- **Immutability**: Favor immutable objects. Make classes and fields `final` where possible. Use collections from
  `List.of()`/`Map.of()` for fixed data. Use `Stream.toList()` to create immutable lists.
- **Streams and Lambdas**: Use the Streams API and lambda expressions for collection processing. Employ method
  references (e.g., `stream.map(Foo::toBar)`).
- **Null Handling**: Avoid returning or accepting `null`. Use `Optional<T>` for possibly-absent values and `Objects`
  utility methods like `equals()` and `requireNonNull()`.

### Naming Conventions

- Follow Google's Java style guide:
    - `UpperCamelCase` for class and interface names.
    - `lowerCamelCase` for method and variable names.
    - `UPPER_SNAKE_CASE` for constants.
    - `lowercase` for package names.
- Use nouns for classes (`UserService`) and verbs for methods (`getUserById`).
- Avoid abbreviations and Hungarian notation.

### Common Bug Patterns

Below are concise, human-readable rules you can apply regardless of which static analysis tool you use. If you run
Sonar/SonarLint, the IDE will show the matching rule and location — direct Sonar connections are preferred and should
override this ruleset.

- Resource management — Always close resources (files, sockets, streams). Use try-with-resources where possible so
  resources are closed automatically.
- Equality checks — Compare object equality with `.equals()` or `Objects.equals(...)` rather than `==` for
  non-primitives; this avoids reference-equality bugs.
- Redundant casts — Remove unnecessary casts; prefer correct generic typing and let the compiler infer types where
  possible.
- Reachable conditions — Avoid conditional expressions that are always true or false; they indicate bugs or dead code
  and should be corrected.

For contributors who *do* use Sonar or SonarLint: the IDE/scan will show the specific rule key (for example, S2095 for
resource leaks) and the affected file/line. Use that information to navigate to the exact location, then apply the
recommended remediation.

### Common Code Smells

These patterns are phrased for humans; they map cleanly to checks in Sonar, SpotBugs, PMD, or Checkstyle but do not
require those tools to be useful.

- Parameter count — Keep method parameter lists short. If a method needs many params, consider grouping into a value
  object or using the builder pattern.
- Method size — Keep methods focused and small. Extract helper methods to improve readability and testability.
- Cognitive complexity — Reduce nested conditionals and heavy branching by extracting methods, using polymorphism, or
  applying the Strategy pattern.
- Duplicated literals — Extract repeated strings and numbers into named constants or enums to reduce errors and ease
  changes.
- Dead code — Remove unused variables and assignments. They confuse readers and can hide bugs.
- Magic numbers — Replace numeric literals with named constants that explain intent (e.g., MAX_RETRIES).

If you run a static analyzer like Sonar or SonarLint — direct Sonar connections are preferred and should override this
ruleset. Sonar rule keys are useful for automation and suppression, but they are not required in day-to-day developer
guidance.

## Comments & Javadoc

The project's Checkstyle configuration enables the `JavadocMethod` and `JavadocStyle` checks (see `scripts/ci/checkstyle.xml`).
The guidance below reflects those active rules so your Javadoc and inline comments will pass the project's style checks
and remain helpful to future readers.

- What Checkstyle enforces (summary):
  - `JavadocMethod`: public and protected methods should have Javadoc comments describing their behavior and tags for
    parameters, return values, and thrown exceptions where applicable.
  - `JavadocStyle`: Javadoc must use a proper, consistent style — a short summary sentence (capitalised and ending with a
    period), followed by any additional description and appropriate `@param`, `@return`, and `@throws` tags.

- Practical rules to follow:
  - Add Javadoc to all public and protected methods. Package-private and private methods should have Javadoc when their
    behavior is non-obvious or complex, but they are not strictly enforced by the current Checkstyle configuration.
  - Start the Javadoc with a single-line summary (one sentence). The summary must start with a capital letter and end
    with a period.
  - When more detail is needed, separate the one-line summary from the rest of the description with a blank line.
  - Include `@param` entries for each parameter, `@return` for non-void methods, and `@throws` for checked exceptions the
    method may throw. Each tag should contain a short, imperative-style explanation.
  - Keep descriptions concise and avoid repeating the method or parameter names verbatim in the description.
  - Prefer plain text and simple inline tags; avoid HTML where possible and avoid long paragraphs inside a tag.
  - Use `{@code ...}` for inline code fragments and `{@link ClassName}` for cross-references when helpful.
  - For small, obvious getters/setters or trivial methods, a short one-line Javadoc is sufficient. If the method is trivial
    and self-explanatory, consider omitting Javadoc for private methods.
  - Use inline comments (`//` or `/* ... */`) sparingly to explain "why" when the intent isn't obvious; prefer expressive
    names and small helper methods to reduce the need for comments.

- Notes about types & fields:
  - The `JavadocType` and `JavadocVariable` checks are currently commented out in the repository's `checkstyle.xml`, so
    class/type and field-level Javadoc are not strictly required by Checkstyle. However, public APIs (classes, interfaces,
    enums) should still include Javadoc describing their purpose and usage.

- Examples:
  - Good Javadoc for a method:

    /**
     * Calculates the total price, including discounts and taxes.
     *
     * @param basePrice the base price before discounts
     * @param discountRate percentage discount to apply (0.0 - 1.0)
     * @return the final price after applying discount and taxes
     * @throws IllegalArgumentException if basePrice is negative or discountRate is outside 0.0-1.0
     */

## Build and Verification

- After adding or modifying code, verify the project continues to build successfully.
- If the project uses Maven, run `mvn clean install`.
- If the project uses Gradle, run `./gradlew build` (or `gradlew.bat build` on Windows).
- Ensure all tests pass as part of the build.