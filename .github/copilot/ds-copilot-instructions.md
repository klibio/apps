# Copilot instructions for OSGi Declarative Services (Compendium)

This companion guide focuses on Declarative Services (DS) usage alongside the OSGi Core R8 instructions in the repository root file. Prefer DS over manual service registration when the project allows Compendium APIs.

## Authoritative references

- DS spec (Compendium R8): https://docs.osgi.org/specification/osgi.cmpn/8.0.0/service.component.html
- DS annotations Javadoc (R8): https://docs.osgi.org/javadoc/osgi.cmpn/8.0.0/org/osgi/service/component/annotations/package-summary.html
- Metatype annotations (for typed configs): https://docs.osgi.org/javadoc/osgi.cmpn/8.0.0/org/osgi/service/metatype/annotations/package-summary.html
- Component Property Types: https://docs.osgi.org/javadoc/osgi.cmpn/8.0.0/org/osgi/service/component/propertytypes/package-summary.html
- bnd tooling (generates DS component descriptors): https://github.com/bndtools/bnd
- Example patterns (archived): https://github.com/osgi/v2archive.osgi.enroute

## Defaults and key concepts

- Components providing a service are lazy (activated on demand) unless `immediate = true`.
- Components without a provided service are immediate by default.
- Activation happens via `@Activate` (constructor or method); deactivation via `@Deactivate`; configuration changes via `@Modified`.
- References are declared with `@Reference` and can be static/dynamic, greedy/reluctant, and with cardinality: `MANDATORY`, `OPTIONAL`, `MULTIPLE`.
- Prefer typed configuration using `@ObjectClassDefinition` and `@Designate`.
- Prefer Component Property Types for common/typed service properties.

## Provider example (single service)

Intent: Provide `Greeter` service with an English implementation.

Contract
- Input: none (provided service)
- Output: OSGi service `Greeter` registered with property `language=en`
- Success: Component is activated on first bind; cleanly deactivated

Shape Copilot should generate:
- API: `com.example.api.Greeter { String greet(String name); }`
- Provider component:
  - `@Component(service = Greeter.class)`
  - Optional property type: `@ServiceVendor("Example Co")` or manual property map
  - `@Activate` method receiving `ComponentContext` or `GreeterConfig`
  - `@Deactivate` void method
  - Method implementation of `greet`

Notes
- Use a property type or `@Component(property = {"language=en"})` to tag the service.
- Avoid heavy work in `@Activate`; defer long tasks to background services.

## Consumer example (mandatory reference)

Intent: Consume a single `Greeter` service with `language=en`.

Shape Copilot should generate:
- `@Component(immediate = true)`
- Field or method reference:
  - `@Reference(target = "(language=en)", policy = ReferencePolicy.STATIC, cardinality = ReferenceCardinality.MANDATORY)`
  - Inject a `Greeter` field
- Optional `@Activate` to log readiness

Notes
- Prefer static references unless you need dynamic swap behavior.
- Use LDAP filters to select appropriate providers.

## Multiple/optional references

- OPTIONAL: `@Reference(cardinality = ReferenceCardinality.OPTIONAL)`; guard against null usage.
- MULTIPLE: inject a `List<T>` or `Collection<T>`; specify `policyOption = GREEDY` if you prefer the best-ranked services.
- Dynamic updates: set `policy = DYNAMIC` and provide bind/unbind methods (`addX`, `removeX`) or use field/collection injection with dynamic policy.

## Configuration: typed and factory

Typed configuration for a component:
- Define `@ObjectClassDefinition` interface `GreeterConfig` with attributes (e.g., `String salutation()` and `String language() default "en";`).
- Annotate the component with `@Designate(ocd = GreeterConfig.class)`.
- Inject configuration in `@Activate`/`@Modified` method or constructor.
- Use values to configure behavior and set service properties as needed.

Factory configuration:
- `@Designate(ocd = GreeterConfig.class, factory = true)` creates multiple component instances; ensure the component is designed to handle multiple configs.

## Component Property Types

Prefer property types for common DS properties instead of raw strings:
- Example annotations: `@ServiceRanking`, `@ServiceVendor`, `@ServiceDescription`.
- You can create custom property types with `@ComponentPropertyType` to ensure type-safe property names and values.

## Error handling and lifecycle safety

- Keep `@Activate` and `@Deactivate` short and idempotent; close resources in `@Deactivate`.
- Avoid throwing unchecked exceptions from lifecycle methods; fail fast and log appropriately.
- For dynamic references, guard concurrent access (e.g., `volatile` fields or synchronized collections) when swapping services.

## Do and Don’t (DS)

Do
- Prefer DS over manual registry when allowed.
- Use interface-based services and keep implementations package-private.
- Use typed configuration and property types for clarity and safety.
- Choose static vs dynamic references deliberately; document the choice.

Don’t
- Instantiate services manually; let DS handle lifecycle and injection.
- Use start levels for dependency ordering.
- Bypass DS by calling the service registry directly in DS components (except for advanced cases).

## Tooling notes (bnd)

- Ensure annotation processing is enabled so bnd generates `OSGI-INF/*.xml` component descriptors from DS annotations.
- Keep packages exporting only API; implementation stays internal. bnd will compute imports and track package versions.
- In Gradle with the bnd plugin, component descriptors and manifests are generated at build-time without additional config in most cases.
- See the dedicated bnd guide: bnd-copilot-instructions.md

## Prompts to use in this workspace (DS)

- "Create a DS provider component for `Greeter` with a typed config (`language` defaulting to `en`) and service property reflecting the language."
- "Generate a DS consumer with a mandatory static reference to `Greeter` filtered by `(language=en)`, and log on activation."
- "Show a DS component with multiple `Greeter` references, greedy policy, and safe concurrent updates."
- "Add a custom Component Property Type annotation `@GreeterLanguage` and use it on a provider."

## Testing pointers

- Consider `org.osgi.test.junit5` for integration tests against a minimal framework runtime; use service assertions and bundle lifecycles.
- See the dedicated testing guide: osgi-test-copilot-instructions.md
 - If exposing REST endpoints, see the Servlet Whiteboard guide for configuration: osgi-servlet-whiteboard.md

---

See also: the root `.copilot-instructions` for Core guidance and general practices.
