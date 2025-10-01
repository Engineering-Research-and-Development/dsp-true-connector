# TCK Compliancy

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

| Module                   | Description                                                                                          | Result       |
|--------------------------|------------------------------------------------------------------------------------------------------|--------------|
| dsp-metadata             | Tests for the metadata endpoint in package org.eclipse.dataspacetck.dsp.verification.metadata        | 100% passed  |
| dsp-catalog              | Tests for the catalog protocol in package org.eclipse.dataspacetck.dsp.verification.catalog          | 100% passed. | 
| dsp-contract-negotiation | Tests for the contract negotiation protocol in package org.eclipse.dataspacetck.dsp.verification.cn. | 0% passed.   |                                                              
| dsp-transfer-process     | Tests for the transfer process protocol in package org.eclipse.dataspacetck.dsp.verification.tp      | 100% passed. |

```text
[2025-10-01T12:09:43.7705941] Passed tests: 34
[2025-10-01T12:09:43.7705941] Failed tests: 31
```
