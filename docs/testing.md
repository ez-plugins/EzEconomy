---
title: Testing
nav_order: 3
parent: Developer Reference
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
- Real runtime (Paper/Folia command-path) tests:
  - Profile: `paper-folia-runtime-it`
  - Includes only `*RuntimeIT` classes (excluded from default integration lane)
  - Example (Paper):
    - `mvn -pl ezeconomy-bukkit -Ppaper-folia-runtime-it -Dit.test=PayCommandAsyncRuntimeIT -Dezeconomy.runtime.it.enabled=true -Dezeconomy.runtime.server=paper verify`
  - Example (Folia):
    - `mvn -pl ezeconomy-bukkit -Ppaper-folia-runtime-it -Dit.test=PayCommandAsyncRuntimeIT -Dezeconomy.runtime.it.enabled=true -Dezeconomy.runtime.server=folia verify`

Readability best practices

- Use clear variable names (given/when/then style helps).
- Avoid complex loops or logic inside tests—extract helpers where necessary.
- Use expressive assertion libraries (AssertJ) when available.

CI recommendations

- Run `mvn test jacoco:report jacoco:check` in CI for modules changed by a PR.
- Fail builds when coverage drops below the configured threshold.

Smoke test policy

- PR smoke matrix (fast lane):
  - Paper `1.19.4` on Java 17 (legacy Bukkit artifact)
  - Paper `1.20.6` on Java 21 (modern Bukkit artifact)
  - Folia `1.20.6` on Java 21 (modern Bukkit artifact)
  - Paper `1.21.11` on Java 21
  - Paper `1.21.11` on Java 25
- Nightly/workflow-dispatch smoke matrix (expanded lane):
  - Paper `1.17.1`, `1.18.2`, `1.19.4`, `1.20.6` on Java 17
  - Folia `1.20.6` on Java 17
  - Paper `1.21.11` on Java 21
  - Spigot `1.20.6` on Java 17 (BuildTools)
- Artifact split guidance:
  - Use the legacy Bukkit artifact for Java 17 runtime lanes.
  - Use the modern Bukkit artifact for Java 21+ runtime lanes.
- Startup assertions must include:
  - plugin enable line present
  - no `UnsupportedClassVersionError`, `NoSuchMethodError`, `NoClassDefFoundError`
  - no `UnsupportedOperationException` from scheduler misuse
  - no `ERROR` lines attributed to EzEconomy

Templates & examples

- See `docs/test-templates/UnitTestTemplate.md` for a minimal, readable unit test template.

Further improvements (optional)

- Add a parent-level JaCoCo plugin configuration to enforce consistent thresholds across modules.
- Provide a small in-repo test-helpers module with common fixtures and builders for reuse.
