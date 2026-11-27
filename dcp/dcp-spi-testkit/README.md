# dcp-spi-testkit

Abstract JUnit 5 contract tests for the DCP participant context SPI in `dcp-common`
(`it.eng.dcp.common.participant`). A host that implements the SPI subclasses the contracts to prove that its
implementation keeps participants apart. The kit is in-process and complements the DSP and DCP TCKs, which
test the wire protocol.

## Contracts

| Contract class | SPI under test | Rules checked |
|---|---|---|
| `ParticipantContextProviderContractTest` | `ParticipantContextProvider` | An unknown ID returns empty. A disabled participant is reported as `DISABLED` and `findEnabled` never returns it. Each context has exactly one DID and a signing key reference. DIDs are unique across participants. |
| `ParticipantContextResolverContractTest` | `ParticipantContextResolver` | An unscoped request never resolves to an arbitrary participant in multi-participant mode. Reserved IDs are never resolved. Resolution is deterministic. A request addressed to a participant resolves to it. |
| `ParticipantKeyProviderContractTest` | `ParticipantKeyProvider` | The active key signs and the published active key verifies the signature. After rotation the previous public key stays published (inactive) for the grace period and is removed once it has elapsed. Keys of different participants are never shared, and rotating one participant leaves the other's keys untouched. |

The key provider contract also proves that the signing key reference of a context resolves to a usable key: it signs
with the key the provider returns for the context. The contract uses ES256 and expects P-256 (secp256r1) keys, the
curve the DCP modules generate.

The cases that need two participants, or a disabled one, are skipped with JUnit assumptions when the
implementation declares that it serves a single participant or cannot represent a disabled one.

## Add the kit to a host

```xml
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>dcp-spi-testkit</artifactId>
    <version>${revision}</version>
    <scope>test</scope>
</dependency>
```

The JUnit 5 API, AssertJ and `spring-test` (for `MockHttpServletRequest`) are compile-scope dependencies of the
kit, so they reach the host's test classpath transitively.

## Subclass a contract

Extend the contract, implement its abstract fixture methods and add nothing else; the inherited `@Test`
methods run as part of your test suite. JUnit creates one instance per test, so build the fixtures in
`@BeforeEach` and never share state statically. The key provider contract rotates keys, so it needs fresh key
storage for every test.

```java
class TenantParticipantContextProviderContractTest extends ParticipantContextProviderContractTest {

    private TenantParticipantContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TenantParticipantContextProvider(/* two enabled tenants and one disabled tenant */);
    }

    @Override
    protected ParticipantContextProvider provider() {
        return provider;
    }

    @Override
    protected ParticipantContext participantA() {
        return provider.find("tenant-a").orElseThrow();
    }

    @Override
    protected ParticipantContext participantB() {
        return provider.find("tenant-b").orElseThrow();
    }

    @Override
    protected boolean supportsMultipleParticipants() {
        return true;
    }

    @Override
    protected Optional<String> disabledParticipantId() {
        return Optional.of("tenant-disabled");
    }
}
```

### Fixture methods

| Contract | Abstract fixtures | Optional overrides |
|---|---|---|
| Provider | `provider()`, `participantA()`, `participantB()`, `supportsMultipleParticipants()`, `disabledParticipantId()` (return `Optional.empty()` if the implementation cannot represent a disabled participant) | `unknownParticipantId()` |
| Resolver | `resolver()`, `requestFor(participantId)` (a request addressed to that participant, for example `/<id>/dcp/...`), `unscopedRequest()`, `participantIdA()`, `participantIdB()`, `supportsMultipleParticipants()` | `reservedIds()` (default: `internal`, `api`, `actuator`, `.well-known`; add your zone prefixes and DCP base paths) |
| Key provider | `keyProvider()`, `participantA()`, `participantB()`, `supportsMultipleParticipants()`, `expireKeyGracePeriod()` (make the grace period of every key retired so far elapse, for example by moving the clock or the retirement timestamps, so the test need not wait) | none |

`participantB()` and `participantIdB()` are only called when `supportsMultipleParticipants()` returns `true`,
so a single-participant implementation may throw `UnsupportedOperationException` from them.

## Defaults run the same contracts

The kit's own tests (`src/test/java/it/eng/dcp/spi/testkit/defaults`) run the contracts against the
`dcp-common` defaults: `SingleParticipantContextProvider`, `FixedParticipantContextResolver` and
`DefaultParticipantKeyProvider` (with a real PKCS12 keystore and an in-memory key metadata store). They live
here rather than in `dcp-common` because `dcp-common` cannot depend on this module without a reactor cycle.

The multi-participant cases are exercised by `PathParticipantContextResolver`, a map-backed provider and a key
provider that routes each participant to its own `DefaultParticipantKeyProvider`.

## Run

```bash
mvn -pl dcp/dcp-spi-testkit -am test
```
