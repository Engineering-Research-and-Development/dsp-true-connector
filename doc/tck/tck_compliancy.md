# TCK Compliancy

## DSP TRUEConnector configuration

Connector must be running using tck profile configuration.
This profile will use initial_data_tck.json file to load test data, that are mandatory for TCK tests.

Other important notice is that it will load services based on profile, which will rely on provided test data
(datasets, contract negotiations, agreements, etc) to initiate next steps of TCK tests.

## TCK

### GitHub Action

[GitHub Action result](https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions/runs/18193969708/job/51795340748)

If you do not see the result, please check if GitHub Action `Run docker container for TCK Runtime` step.

### CMD

Tested against TCK version v1.0.0-RC5

- Cloned TCK project
- Build following instructions on [TCK GitHub](https://github.com/eclipse-dataspacetck/dsp-tck/tree/main)
- Updated configuration file, which can be found [here](sample.tck.properties)
- Used console command:

```
 java -jar dsp/dsp-tck/build/libs/dsp-tck-runtime.jar -config config/tck/sample.tck.properties
```

Console output of [tck run result](tck_compliancy_result.txt)

## Result

| Module                   | Description                                                                                         | Result      |
|--------------------------|-----------------------------------------------------------------------------------------------------|-------------|
| dsp-metadata             | Tests for the metadata endpoint in package org.eclipse.dataspacetck.dsp.verification.metadata       | 100% passed |
| dsp-catalog              | Tests for the catalog protocol in package org.eclipse.dataspacetck.dsp.verification.catalog         | 100% passed | 
| dsp-contract-negotiation | Tests for the contract negotiation protocol in package org.eclipse.dataspacetck.dsp.verification.cn | 0% passed   |                                                              
| dsp-transfer-process     | Tests for the transfer process protocol in package org.eclipse.dataspacetck.dsp.verification.tp     | 100% passed |

```text
[2025-10-01T12:09:43.7705941] Passed tests: 34
[2025-10-01T12:09:43.7705941] Failed tests: 31
```

### Maven profile

There is also maven profile to run TCK tests, skipping ALL junit and integration tests, which can be used as alternative
to above-mentioned command line execution.

```
mvn clean verify -Ptck
```

This will run TCK tests against running connector, based on configuration provided in

- connector/src/test/resources/application-tck.properties
- connector/src/test/resources/initial_data-tck.json

files.
