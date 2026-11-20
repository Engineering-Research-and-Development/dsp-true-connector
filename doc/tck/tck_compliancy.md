# TCK Compliance

## DSP TRUEConnector configuration

Connector must be running using tck profile configuration.
This profile will use initial_data_tck.json file to load test data, that are mandatory for TCK tests.

Other important notice is that it will load services based on profile, which will rely on provided test data
(datasets, contract negotiations, agreements, etc.) to initiate next steps of TCK tests.

## TCK

### GitHub Action

This scenario test the DSP TRUEConnector against TCK in a Dockerized environment using GitHub Action workflow.

[GitHub Action workflow](https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions/workflows/tck_compliance.yml)

### CMD

Tested against TCK version v1.0.0-RC5

- Cloned TCK project
- Built following instructions on [TCK GitHub](https://github.com/eclipse-dataspacetck/dsp-tck/releases/tag/v1.0.0-RC5)
- Updated configuration file, which can be found [here](sample.tck.properties)
- Used console command:

```
 java -jar dsp/dsp-tck/build/libs/dsp-tck-runtime.jar -config config/tck/sample.tck.properties
```

#### Authentication

The protocol routes of the `INTERNAL` mode need `Authorization: Bearer <protocol token>`. The token is an
HS256 JWT signed with `application.security.jwt.secret` with claims `sub` `connector`,
`realm_access.roles` `["CONNECTOR"]`, `iat`, `exp` and `jti` (see
[security.md](../security.md#connector-to-connector-credentials-m2m)). `TCKCompliance` takes it from the
`ConnectorCredentialProvider` bean (`InternalProtocolTokenIssuer`) and sends `Bearer <minted token>`. For a
manual run, replace the `<protocol-token>` placeholder in [sample.tck.properties](sample.tck.properties)
with a minted token; it expires after `application.security.jwt.access-expiration-ms`.

Earlier runs sent a `Basic` header for the formerly seeded connector user. The `INTERNAL` protocol chain
does not accept Basic credentials, so that run failed 63 cases, all with 401. That header was the cause of
the baseline. The user is no longer seeded.

Console output of [tck run result](tck_compliancy_result.txt)

## Result

| Module                   | Description                                                                                         | Result      |
|--------------------------|-----------------------------------------------------------------------------------------------------|-------------|
| dsp-metadata             | Tests for the metadata endpoint in package org.eclipse.dataspacetck.dsp.verification.metadata       | 100% passed |
| dsp-catalog              | Tests for the catalog protocol in package org.eclipse.dataspacetck.dsp.verification.catalog         | 100% passed | 
| dsp-contract-negotiation | Tests for the contract negotiation protocol in package org.eclipse.dataspacetck.dsp.verification.cn | 100% passed |                                                              
| dsp-transfer-process     | Tests for the transfer process protocol in package org.eclipse.dataspacetck.dsp.verification.tp     | 100% passed |

```text
[2025-11-05T13:37:16.806510192] Passed tests: 65
[2025-11-05T13:37:16.806608045] Failed tests: 0

[2025-11-05T13:37:16.806710567] 🎉😃🚀All tests passed
```

### Run with the minted protocol token

With the Bearer header, 65 cases run: 52 pass and 13 fail, with no 401 left. The table above is the result
of an earlier release and is not current for the `INTERNAL` mode run. The 13 failures were hidden
behind the 401s. They are all negotiation cases and all end in "Timeout waiting for state":
`CN_C:01-02`, `CN_C:02-02`, `CN_C:02-03`, `CN:01-01`, `CN:01-02`, `CN:01-03`, `CN:02-01`, `CN:02-05`,
`CN:02-06`, `CN:02-07`, `CN:03-02`, `CN:03-03`, `CN:03-04`.

The cause is not authentication. The connector's outbound callbacks to the TCK mock consumer fail with
"unexpected end of stream" or a timeout, so the negotiation never reaches the expected state. This is a
known, open follow-up.
