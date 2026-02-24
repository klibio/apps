# Copilot instructions: OSGi Servlet Whiteboard for Jakarta REST

This guide helps generate correct code, configuration, and bnd wiring to run Jakarta REST (JAX-RS) via the OSGi Servlet Whiteboard using the Eclipse OSGi Technology implementation based on Jersey.

Authoritative repo: https://github.com/eclipse-osgi-technology/jakartarest-osgi
Whiteboard spec: https://docs.osgi.org/specification/osgi.cmpn/8.1.0/service.jakartars.html
Jersey: https://eclipse-ee4j.github.io/jersey/

## What this project provides

- An OSGi Whiteboard implementation for Jakarta REST backed by Jersey (e.g., Jersey 3.1.x, HK2 3.x as of latest releases).
- Two adapters (choose one runtime):
  - Jetty adapter: `org.eclipse.osgitech.rest.jetty` (embedded Jetty HTTP server)
  - Servlet Whiteboard adapter: `org.eclipse.osgitech.rest.servlet.whiteboard` (attaches to an existing OSGi HTTP Whiteboard implementation)
- Core bundles to include:
  - `org.eclipse.osgitech.rest` — whiteboard implementation
  - `org.eclipse.osgitech.rest.config` — default configuration (optional but helpful)
  - `org.eclipse.osgitech.rest.sse` — optional fragment for Server-Sent Events

This page focuses on the Servlet Whiteboard adapter.

## Dependencies (Maven/Gradle coordinates)

Group: `org.eclipse.osgi-technology.rest`
- Artifacts: `org.eclipse.osgitech.rest`, `.config`, `.sse`, `.servlet.whiteboard` (and `.jetty` if using Jetty runtime)

## OSGi Servlet Whiteboard setup

You run a standard OSGi HTTP Whiteboard (for example Apache Felix HTTP Service) and connect the Jakarta REST Whiteboard runtime to it via a target filter.

- Ensure your OSGi runtime has an HTTP Whiteboard implementation installed (e.g., Apache Felix HTTP with Jetty).
- Install the Jakarta REST bundles listed above plus `org.eclipse.osgitech.rest.servlet.whiteboard`.
- Configure two parts via Config Admin or the OSGi Configurator:
  1) The HTTP Whiteboard runtime (e.g., Apache Felix HTTP)
  2) The Jakarta REST Servlet Whiteboard runtime component

Example Configurator JSON (shape):

- Factory PID for Felix HTTP (service factory): `org.apache.felix.http~demo`
  - Keys (subset):
    - `org.osgi.service.http.port`: 8081
    - `org.osgi.service.http.host`: "localhost"
    - `org.apache.felix.http.context_path`: "demo"
    - `org.apache.felix.http.name`: "Demo HTTP Whiteboard"
    - `org.apache.felix.http.runtime.init.id`: "demowb" (ID used to select this whiteboard)

- Factory PID for Jakarta REST Whiteboard runtime: `JakartarsServletWhiteboardRuntimeComponent~demo`
  - Keys (subset):
    - `jersey.jakartars.whiteboard.name`: "Demo Jakarta REST Whiteboard"
    - `jersey.context.path`: "rest" (base path under the HTTP whiteboard context)
    - `osgi.http.whiteboard.target`: "(id=demowb)" (LDAP filter to bind to the HTTP whiteboard instance above)

This produces a REST base URL like: http://localhost:8081/demo/rest

Notes and pitfalls:
- Port 8080 may already be used by a default HTTP Service; if you don’t want it, set system property `org.osgi.service.http.port=-1` to disable the default.
- `jersey.context.path` default is `/` for the servlet whiteboard adapter; choose a non-root path if you want separation.
- The `osgi.http.whiteboard.target` must match the runtime’s identifier property (e.g., `(id=demowb)` when configured via `org.apache.felix.http.runtime.init.id`).

## Jetty vs Servlet Whiteboard (quick compare)

- Jetty adapter config keys (e.g., `jersey.port`, `jersey.context.path`, `jersey.jakartars.whiteboard.name`) control an embedded server; default context path is `/rest`.
- Servlet Whiteboard adapter binds to an external HTTP Whiteboard; use `osgi.http.whiteboard.target` to select the instance; default context path is `/`.
- Pick one adapter per runtime; don’t run both for the same app unless you know why.

## Registering resources and extensions

With the Jakarta REST Whiteboard, your REST resources and extensions are registered as OSGi services. The project provides helpful meta-annotations to simplify component properties.

Shape Copilot should generate for a resource:
- Use DS component with prototype scope: `@Component(service = DemoResource.class, scope = ServiceScope.PROTOTYPE)`
- Apply Jakarta REST annotations: `@Path("/")`, `@GET`, etc.
- Add whiteboard meta-annotations:
  - `@JakartarsResource` to mark as a resource
  - `@JakartarsName("demo")` to name the resource set (optional)

Minimal outline:
- A class annotated with `@Path`, methods with HTTP verbs (`@GET`, etc.)
- Registered as a DS component service; properties contributed by meta-annotations

Provider configuration and selection:
- Use service properties and/or meta-annotations to associate resources with a particular whiteboard if running multiple instances.
- For advanced scenarios, provide extensions as services (filters, exception mappers, etc.) with corresponding meta-annotations.

## bnd integration (helpful shortcuts)

- Add bnd library: `-library: jakartaREST` to get a repository of required Jersey/Jakarta REST artifacts in a bnd workspace.
- Enable Jakarta REST in `.bndrun`: `-library: enableJakartaREST` to auto-populate `-runbundles` with Jersey + Whiteboard dependencies.
- For project builds, include the library dependency:
  - `org.eclipse.osgi-technology.rest:org.eclipse.osgitech.rest.bnd.library:${version}`
  - `org.eclipse.osgi-technology.rest:org.eclipse.osgitech.rest.bnd.project.library:${version}` (for `.bndrun` helpers)

## Testing pointers

- Use the osgi-test JUnit 5 approach to launch a minimal runtime with the HTTP Whiteboard, Jakarta REST bundles, and your resources.
- Inject `BundleContext` and/or your service resources; await service registrations before invoking endpoints (or test via HTTP if the runtime is brought up).
- See: copilot/osgi-test-copilot-instructions.md

## Do and Don’t

Do
- Keep resources small, stateless, and prototype-scoped.
- Use DS and meta-annotations to publish resources cleanly.
- Use Config Admin/Configurator for environment-specific HTTP settings.
- Leverage bnd libraries and `.bndrun` to resolve complete runtimes.

Don’t
- Hardcode HTTP ports/contexts in code; prefer configuration.
- Mix Jetty and Servlet Whiteboard adapters for the same deployment without a clear reason.
- Register implementation packages for export; export only APIs if any.

## Prompts to use in this workspace

- "Create a DS resource component using the Jakarta REST Whiteboard meta-annotations with base path `/hello` and a `GET` that returns `Hello World`."
- "Generate Configurator JSON to configure Felix HTTP at port 8081 with context `demo` and attach the Jakarta REST Servlet Whiteboard at `/rest`."
- "Produce a `.bndrun` that uses `-library: enableJakartaREST` and includes the Servlet Whiteboard adapter to run my resource."

## See also

- Core guidance: ../.copilot-instructions
- DS guidance: ds-copilot-instructions.md
- bnd guidance: bnd-copilot-instructions.md
- osgi-test guidance: osgi-test-copilot-instructions.md
