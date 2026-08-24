# Profiles

Since DSP TRUEConnector is based on Spring Boot, it supports standard Spring profile-based
configuration.

Spring loads configuration files in this order:

1. `application.properties`
2. `application-{profile}.properties` for each active profile

That means the base `application.properties` file provides the default values, while the
profile file only overrides the keys that need to change for that role. Any property that is
not redefined in the profile file is still taken from `application.properties`.

In this repository, the connector and all standalone Data Plane modules follow that pattern:
the profile-specific files are **overrides**, not full standalone configurations.

Those profiles are used as follows:

## Locally running 2 instances (from IDE)

When running 2 instances of connector, simulating or testing interaction between "consumer"
and "provider", you should pass the Spring profile to the Spring Boot application. The default
supported profiles are `consumer` and `provider`.

There are 2 local application property files that correspond to those 2 profiles:

 - application-consumer.properties
 - application-provider.properties
 
located in `connector/src/main/resources` folder.

The same pattern is now used by all standalone Data Plane modules under
`data-plane/*/src/main/resources`:

- `application-consumer.properties`
- `application-provider.properties`

For local development, these profile files align each Data Plane with the matching connector role:

| Module | Consumer profile | Provider profile |
| --- | --- | --- |
| `data-plane-http-pull` | port `9090`, registers to `http://localhost:8080` | port `9092`, registers to `http://localhost:8090` |
| `data-plane-http-push` | port `9093`, registers to `http://localhost:8080` | port `9091`, registers to `http://localhost:8090` |
| `data-plane-grpc` | REST `9094`, gRPC `9095`, registers to `http://localhost:8080` | REST `9096`, gRPC `9097`, registers to `http://localhost:8090` |
| `data-plane-kafka` | port `9098`, registers to `http://localhost:8080` | port `9099`, registers to `http://localhost:8090` |

Each profile also aligns the Mongo database name, default dataplane ID, bucket name, and
encryption key with the matching connector profile.

## What a profile file overrides

For Data Planes, `application-consumer.properties` / `application-provider.properties`
typically override only role-specific values such as:

- `server.port`
- `spring.application.name`
- `spring.data.mongodb.uri`
- `dataplane.id`
- `dataplane.endpoint`
- `dataplane.control-plane-admin-endpoint`
- `s3.bucketName`
- `application.encryption.key`
- `grpc.server.port` for the gRPC Data Plane

Shared values such as `dataplane.api-key`, `dataplane.control-plane-admin-secret`,
`s3.endpoint`, `s3.accessKey`, `s3.secretKey`, `s3.region`, and TLS defaults remain in the base
`application.properties` unless you explicitly override them.

## Activating a profile

Profiles can be activated in the standard Spring Boot ways, for example:

```bash
java -jar data-plane-http-pull.jar --spring.profiles.active=consumer
```

or

```bash
export SPRING_PROFILES_ACTIVE=provider
java -jar data-plane-grpc.jar
```

If no profile is active, Spring uses only `application.properties`.

## Deployment guidance

If you deploy a Data Plane using the profile files shipped in this repository, keep both files
available:

- `application.properties`
- `application-{profile}.properties`

This is required because the shipped profile files contain only overrides, not the full set of
properties.

If you prefer externalized deployment configuration, you have two valid options:

1. Provide a complete `application.properties` and do not activate a profile.
2. Provide a base `application.properties` plus a smaller `application-{profile}.properties`
   override file and activate that profile.

The role-specific property files use different ports and different Mongo database names so that
you can run multiple local instances at the same time without collisions.

Another important profile-related file is `initial_data.json`, which is used to populate MongoDB
with connector metadata, users, properties, and other information required to distinguish
between 2 running instances. The same naming convention is used for this file:
`initial_data-consumer.json` and `initial_data-provider.json` are located in the same directory.


## Maven build

The Maven build does not activate these runtime Spring profiles automatically. Tests rely on the
resource files present in the relevant module and test-resource directories.
 

## Containerized instance

When running a connector or Data Plane as a container, it is not mandatory to use a Spring
profile. If your setup needs one, pass it with:

```
environment:
  - "SPRING_PROFILES_ACTIVE={DESIRED_PROFILE}"

```

Replace `{DESIRED_PROFILE}` with `consumer` or `provider`.

When a profile is active, Spring applies the matching `application-{profile}.properties`
overrides on top of `application.properties`.
