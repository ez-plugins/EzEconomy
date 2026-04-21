---
title: Testing
nav_order: 9
---

# Testing Guidelines for EzEconomy

Purpose
- Provide clear, readable, and useful tests that document expected behavior and prevent regressions.

Running tests
- Run all module tests and generate JaCoCo reports:

```bash
mvn test jacoco:report
```

- Run tests for a single module (replace `<module>` with the module artifact id):

```bash
mvn -pl <module> test jacoco:report
```

Coverage & thresholds
- Use JaCoCo to collect coverage. Aim for a sensible baseline (80% lines) and raise thresholds for critical modules (90%+ for `core`, `storage`).
- Configure `jacoco:check` in CI to enforce thresholds and fail the build on regressions.

Test structure and naming
- Use Arrange / Act / Assert.
- Name tests descriptively: `methodUnderTest_condition_expectedResult` or `shouldDoXWhenY`.
- Keep tests small and focused (one logical behavior per test).
- Prefer builders/fixtures for setup to keep tests readable.

Unit vs Integration
- Unit tests: fast, isolated, mock external dependencies.
- Integration tests: test interactions with DB, Redis, or actual plugin hooks; run them separately (profile or naming suffix `IT`).

Readability best practices
- Use clear variable names (given/when/then style helps).
- Avoid complex loops or logic inside tests—extract helpers where necessary.
- Use expressive assertion libraries (AssertJ) when available.

CI recommendations
- Run `mvn test jacoco:report jacoco:check` in CI for modules changed by a PR.
- Fail builds when coverage drops below the configured threshold.

Templates & examples
- See `docs/test-templates/UnitTestTemplate.md` for a minimal, readable unit test template.

Further improvements (optional)
- Add a parent-level JaCoCo plugin configuration to enforce consistent thresholds across modules.
- Provide a small in-repo test-helpers module with common fixtures and builders for reuse.
