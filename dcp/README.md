# DCP Module Auto-configuration

This module provides an auto-configuration for the DCP feature set so it will register its beans when the `dcp` JAR is present on the application's classpath.

What the auto-configuration does

- Registers `it.eng.dcp.autoconfigure.DcpAutoConfiguration` via Spring Boot's auto-configuration discovery (file: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`).
- Binds the configuration properties under the `dcp.*` prefix to `it.eng.dcp.config.DcpProperties` (enabled via `@EnableConfigurationProperties` in the auto-config).
- Imports `DCPMongoConfig` to enable the module's Mongo repositories and related configuration.
- Component-scans only the `it.eng.dcp` package to register controllers, services and other beans belonging to this module.

Important implementation notes

- DcpProperties must NOT be annotated with `@Component` or other stereotype annotations. It should be a plain `@ConfigurationProperties` class. The auto-configuration enables the properties binding, which creates the single properties bean. If `DcpProperties` is also annotated with `@Component`, you'll end up with two beans and ambiguous injection errors (this was the issue seen earlier).

Configuration control

- The auto-configuration is conditional on the property `dcp.enabled`.
  - To disable the whole module auto-configuration, set:

    dcp.enabled=false

  - By default the auto-configuration is enabled.

Example usage

- To enable the module (default): nothing to do if the `dcp` JAR is on the classpath.
- To disable the module, add to `application.properties` (main app):

    dcp.enabled=false

Testing and verification

- Start the application with the `dcp` module on the classpath and confirm that beans like `it.eng.dcp.service.SelfIssuedIdTokenService` are present.
- If you want an automated check, I can add a small Spring Boot test in the `dcp` module that uses `ApplicationContext` to assert the presence or absence of key beans depending on `dcp.enabled`.

Notes for the main application

- Since you removed package scans in `ApplicationConnector.java`, the DCP module will rely on its auto-configuration to register its beans. Ensure the `dcp` module is included as a dependency (it already is in the parent/connector POM). Also ensure the `META-INF/spring/...AutoConfiguration.imports` resource is packaged into the JAR (Maven does this by default when placed under `src/main/resources`).

If you want, I can:
- Add a small unit/integration test in `dcp` that verifies the auto-config behavior (enabled vs disabled).
- Add more granular conditional properties (for example `dcp.mongo.enabled`) if you want parts of the module conditionally applied.

