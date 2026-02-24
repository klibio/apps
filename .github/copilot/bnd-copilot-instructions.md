# Copilot instructions for bnd and OSGi builds

Use this guide to generate correct OSGi bundles and runnable test environments with bnd/bndtools in Gradle or Maven. Pair it with the Core, DS, and osgi-test guides.

## Authoritative references

- bnd/bndtools repository: https://github.com/bndtools/bnd
- Documentation lives in the repository (docs, examples, Gradle/Maven plugins). Refer to README and docs subfolders.

## Core principles bnd enforces/assists

- Package-first design: Export only API packages; keep implementation internal.
- Compute Import-Package automatically from bytecode; avoid hardcoded classpath entries.
- Generate manifests (Bundle-SymbolicName, Version, Import-Package, Export-Package, Provide/Require-Capability).
- Track and enforce package versioning (semantic versioning) via Baseline.
- Process DS annotations to generate OSGI-INF component descriptors.
- Resolve and run frameworks via .bndrun with the OSGi Resolver.

## Typical project shapes

- bnd Workspace (cnf/ + bundles/ …) in Bndtools IDE, or
- Plain Gradle/Maven project using bnd plugins.

Prefer Gradle with bnd plugin for automation in CI. Keep API and impl in separate modules (bundles).

## Key files and concepts

- bnd.bnd: Project-level defaults and instructions (Bundle-*, Export-Package, -dsannotations, -includeresource, -runbundles, etc.).
- build.gradle with bnd plugin: Generates manifests and processes annotations.
- .bndrun: Defines a resolvable, runnable runtime (required/optional capabilities); used for running and testing.

## Manifest headers (bnd-generated)

- Bundle-SymbolicName: set explicitly or derive from project name.
- Bundle-Version: align with your release/versioning strategy.
- Export-Package: list API packages with versions.
- Import-Package: computed by bnd; apply version ranges automatically.
- Provide-Capability/Require-Capability: added by DS and other annotations as needed.

## Declarative Services processing

- Ensure DS annotations are on the classpath.
- bnd will generate `OSGI-INF/*.xml` descriptors; you may control via `-dsannotations: *` or defaults.
- Component Property Types are supported; properties appear in the generated descriptors.

## Package versioning and Baseline

- Version API packages using `org.osgi.annotation.versioning.Version` in `package-info.java`, or define in bnd instructions.
- Enable Baseline to compare against previous released artifacts and enforce semver changes.
- Typical policy: breaking changes → major; additive binary-compatible changes → minor; fixes → micro.

## bnd in Gradle (shape Copilot should generate)

- Apply plugin and minimal configuration to build OSGi bundles.
- Define `bnd.bnd` for headers and exports.
- Optionally add Baseline task configuration.
- Use `.bndrun` files to resolve and run tests.

## bndrun and resolver

- Create `.bndrun` with `-runrequires` listing your application requirements; resolver computes `-runbundles`.
- Include DS/Config Admin when running DS-based apps.
- Use resolve task to keep `-runbundles` up to date.

## Testing with bnd and osgi-test

- Combine bnd-generated bundles and `.bndrun` with the osgi-test libraries for JUnit 5.
- Run tests inside an OSGi framework; inject BundleContext and services.
- See the osgi-test guide for patterns and examples.

## Do and Don’t (bnd)

Do
- Let bnd compute Import-Package and generate the manifest.
- Version exported API packages and enforce Baseline.
- Keep implementation packages unexported.
- Use `.bndrun` and the resolver to define and maintain your runtime.

Don’t
- Hand-write full manifests or Require-Bundle.
- Export implementation packages or leak internals.
- Bypass Baseline for public API changes.

## Prompts to use in this workspace (bnd)

- "Create a bnd.bnd that exports `com.example.api` at 1.2.0, computes imports, and enables DS processing."
- "Add Gradle bnd plugin configuration to build an OSGi bundle and generate component descriptors."
- "Create a `.bndrun` that resolves a DS-based app with Config Admin and lists runbundles."
- "Configure Baseline to compare against the previous release and fail on incompatible API changes."

## See also

- Core guidance: ../.copilot-instructions
- Declarative Services guidance: ds-copilot-instructions.md
- OSGi testing with osgi-test: osgi-test-copilot-instructions.md
- Jakarta REST via OSGi Servlet Whiteboard: osgi-servlet-whiteboard.md
