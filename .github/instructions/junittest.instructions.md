--
applyTo: "**/*Test.java"
---

# Instructions for Creating New JUnit 5 Tests

Follow these guidelines when creating new JUnit 5 tests for this project. These instructions ensure consistency,
maintainability, and high code quality.

## 1. Test Class Structure

- Name the test class `<ClassUnderTest>Test.java` and place it in the corresponding test package.
- Use JUnit 5 (`org.junit.jupiter`), not JUnit 4.

## 2. Annotations and Setup

- Use `@ExtendWith(MockitoExtension.class)` if mocking is required.
- Use `@Mock` for all dependencies of the class under test.
- Use `@InjectMocks` for the class under test.

## 3. Test Methods

- Annotate each test method with `@Test`.
- Use `@DisplayName` to provide a clear, human-readable description for each test.
- Use `org.junit.jupiter.api.Assertions.*` for assertions.
- Use static imports for assertion methods (e.g., `assertEquals`, `assertThrows`).
- For methods with multiple input variations, use `@ParameterizedTest` with `@ValueSource`, `@CsvSource`, or
  `@MethodSource`.

## 4. Coverage Requirements

- Ensure at least 80% code coverage, including line and branch coverage.
- Write tests for all public methods.
- Cover all logical branches (e.g., if/else, switch, try/catch).

## 5. Test Scenarios

- Write both positive (success) and negative (failure/exception) tests for each method.
- For negative scenarios, use `assertThrows` to verify exceptions.

## 6. Mocking Best Practices

- Use Mockito to control mock behavior (`when`, `doThrow`, etc.).
- Avoid unnecessary stubbing; only mock what is required for the test.

## 7. Parameterized Tests

- Use parameterized tests when the same logic is tested with different inputs.
- Annotate with `@ParameterizedTest` and provide input sources.

## 8. General Best Practices

- Keep tests independent and repeatable.
- Use descriptive method names and display names.
- Clean up resources if needed (use `@AfterEach` or `@AfterAll`).

## 9. Tools

- Use JaCoCo or a similar tool to verify code and branch coverage.
- Review coverage reports to ensure all branches and lines are tested.

## 10. Execution

- Run tests frequently during development to catch issues early.
- Ensure all tests pass before committing code.
- Generated tests must pass before proceeding with next steps.

---

By following these instructions, all new tests will be consistent, thorough, and maintainable, and will help ensure high
code quality and coverage.
