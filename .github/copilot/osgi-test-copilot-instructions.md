# Copilot instructions for OSGi testing (JUnit 5 + osgi-test)

This guide helps generate reliable OSGi tests using the Eclipse OSGi Technology osgi-test libraries with JUnit 5. Use it alongside the Core and DS instruction files.

## Authoritative references

- osgi-test (GitHub): https://github.com/eclipse-osgi-technology/osgi-test
- Documentation and examples live in that repository’s README and submodules.
- Javadoc (by version) is linked from the repository releases.

## Goals and defaults

- Prefer JUnit 5 with osgi-test extensions to run tests in an OSGi framework and to inject test fixtures (BundleContext, services, configs).
- Use bnd/bndtools or Gradle/Maven with bnd to build and run tests inside a framework.
- Keep tests isolated, deterministic, and independent of execution order.

## Common testing patterns Copilot should generate

### 1) BundleContext injection and basic assertions

Intent: Access the framework context and assert bundle/service state.

Shape
- Annotate class to enable osgi-test JUnit 5 extensions (see repo for the exact extension annotation).
- A test method parameter of type `org.osgi.framework.BundleContext` is injected.
- Use `getServiceReferences` with an LDAP filter to locate services; assert they exist/are absent as required.
- Always release services you acquire via `ungetService` or use utilities provided by the extension to manage lifecycle automatically.

### 2) Service injection with filters and timeouts

Intent: Obtain a service by interface (and optional filter) and wait until it’s available.

Shape
- Declare a field or parameter annotated to request a service by interface (and optionally by `(property=value)` filter).
- Configure a reasonable timeout to await service registration in asynchronous scenarios.
- Use the injected service directly; teardown is handled by the extension or by closing any provided resource handles.

Notes
- Prefer interface types and stable filters (avoid implementation classes).
- Keep timeouts modest; surface failures clearly.

### 3) Testing DS components with configuration

Intent: Validate DS components with typed or dictionary-based configuration.

Shape
- Ensure your DS provider is part of the test runtime.
- Push Config Admin configuration for the component PID before asserting service activation.
- Verify activation (e.g., service becomes available, properties reflect configuration).
- Modify configuration and verify `@Modified` behavior if applicable.

### 4) Installing test bundles and cleaning up

Intent: Dynamically install a temporary bundle for a test.

Shape
- Use `BundleContext.installBundle` (from an InputStream or location), `start`, and later `stop` + `uninstall`.
- Prefer try/finally or extension-provided cleanup hooks.

### 5) Assertions and utilities

- Use osgi-test-provided assertions/utilities (see repo) for concise service existence checks and awaiting conditions.
- When rolling your own, prefer Awaitility-style polling with explicit timeouts over sleeps.

## Do and Don’t

Do
- Use JUnit 5 and the osgi-test extensions to inject BundleContext/services cleanly.
- Use LDAP filters and service properties to select the correct service under test.
- Keep tests framework-agnostic (don’t rely on a specific OSGi implementation).
- Ensure cleanup of installed bundles and acquired services.

Don’t
- Depend on bundle start levels or activation order.
- Assert on implementation classes directly; test via service interfaces and published properties.
- Use arbitrary Thread.sleep; prefer await helpers with timeouts.

## Tooling notes

- With bnd (Gradle/Maven), tests can run in an embedded framework; ensure test runtime includes the bundles under test and required compendium services (e.g., DS, Config Admin) if needed.
- Place API and implementation into separate bundles as in production; wire them in the test runtime via Import-/Export-Package.
- See the bnd guide for `.bndrun` usage and resolver-based runtimes: bnd-copilot-instructions.md

## Prompts to use in this workspace (osgi-test)

- "Create a JUnit 5 test that injects BundleContext, waits for a `Greeter` service with `(language=en)`, and asserts greet() returns 'Hello, Alice'."
- "Generate a DS component integration test that applies a Config Admin configuration and verifies `@Modified` updates behavior."
- "Show a test that installs a temporary bundle in setUp and uninstalls it in tearDown using the osgi-test extensions."

## See also

- Core guidance: ../.copilot-instructions
- Declarative Services guidance: ds-copilot-instructions.md
