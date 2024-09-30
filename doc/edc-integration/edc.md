# EDC testing

This is a guide on how to start the EDC Samples, preparation for communication, requests for testing, and obstacles that we have encountered.

## EDC Samples start

First download the samples from:
https://github.com/eclipse-edc/Samples/tree/main

To run the provider, just run the following command:

```bash
java -Dedc.keystore=transfer/transfer-00-prerequisites/resources/certs/cert.pfx -Dedc.keystore.password=123456 -Dedc.fs.config=transfer/transfer-00-prerequisites/resources/configuration/provider-configuration.properties -jar transfer/transfer-00-prerequisites/connector/build/libs/connector.jar
```

To run the consumer, just run the following command (different terminal):

```bash
java -Dedc.keystore=transfer/transfer-00-prerequisites/resources/certs/cert.pfx -Dedc.keystore.password=123456 -Dedc.fs.config=transfer/transfer-00-prerequisites/resources/configuration/consumer-configuration.properties -jar transfer/transfer-00-prerequisites/connector/build/libs/connector.jar
```

## EDC Samples preparation and communication

You can use this Postman collection:
[TC Postman collection](https://github.com/Engineering-Research-and-Development/dsp-true-connector/edc-integration/edc-sample.postman_collection.json)

The first 4 requests add an asset to the provider:
![EDC provider preparation](doc/edc-integration/edc-provider-preparation.png)

The rest are for the negotiation and data transfer:
![EDC negotiation and data transfer](doc/edc-integration/edc-negotiation-and-data-transfer.png)

After initializing the negotiation and getting the response copy the @id value underlined red:
![EDC contract negotiation id](doc/edc-integration/edc-contract-negotiation-id.png)

Use it in the next request to get the contract negotiation, replace it with the value underlined yellow:
![EDC get contract negotiation](doc/edc-integration/edc-get-contract-negotiation.png)

From the previous response copy the contractAgreementId value underlined red and use in the Start transfer process contractId underlined red:
![EDC start tranfer process](doc/edc-integration/edc-start-tranfer-process.png)

To check the transfer process use the @id from previous response underlined yellow and replace in the Check transfer process request path underlined yellow:
![EDC check transfer process](doc/edc-integration/edc-check-transfer-process.png)

Get the data address by replacing the path in the Data Address underlined yellow with the **@id from the Start transfer process request underlined yellow**:
![EDC data address](doc/edc-integration/edc-data-address.png)

In order to get the data use the authorization value from previous request underlined red and add the Authorization header in Fetch data public request, **set authorization in Authorization tab to NO AUTH**:
![EDC fetch data public](doc/edc-integration/edc-fetch-data-public.png)

## TC preparation

EDC is using a custom authentication method so for the testing we have to turn off our authorization. Go to it.eng.connector.configuration.WebSecurityConfig class and disable lines 125-129:

```
//                            .requestMatchers(new AntPathRequestMatcher("/connector/**"),
//                                    new AntPathRequestMatcher("/negotiations/**"),
//                                    new AntPathRequestMatcher("/catalog/**"),
//                                    new AntPathRequestMatcher("/transfers/**"))
//                            .hasRole("CONNECTOR")
```

Since EDC have the authorization enabled we have to change the following in the class it.eng.tools.util.CredentialUtils:

```
public String getConnectorCredentials() {
		// TODO replace with Daps JWT
//		return  okhttp3.Credentials.basic("connector@mail.com", "password");
		return "{\"region\":\"eu\",\"audience\":\"http://localhost:8090\",\"clientId\":\"TC-provider\"}";
	}
```

audience - is the TC connector - the values is passed from  "counterPartyAddress": "http://localhost:8090" in EDC requests
clientId - this value must the same as "assigner": "TC-provider" in EDC requests

![EDC authorization](doc/edc-integration/edc-authorization.png)


Open the initial_data.json and remove the permission, to do so replacing the following:

```
 "hasPolicy": [{
                    "_id": "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5",
                    "_class": "it.eng.catalog.model.Offer",
                    "permission": [{
							"target": "urn:uuid:TARGET",
                            "action": "USE",
                            "constraint": [{
                                    "leftOperand": "COUNT",
                                    "operator": "EQ",
                                    "rightOperand": "5"
                                }
                            ]
                        }
                    ]
                }
            ],
```

With the code bellow:

```
 "hasPolicy": [{
                    "_id": "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5",
                    "_class": "it.eng.catalog.model.Offer",
                    "permission": []
                }
            ],
```


## TC-EDC communication

In this section we are going to go through the contract negotiation between TC and EDC. Please use the TC postman collection for the TC request in conjunction with the information provided in this guide.

### TC consumer - EDC provider

To continue with this section you had to first insert the asset in EDC. EDC will respond automatically to our requests so we will be sending requests only from TC.

Use the TC Start negotiation Postman request and change the body with this:

```
{
    "Forward-To": "http://localhost:19194/protocol",
    "offer": {
        "@id": "MQ==:YXNzZXRJZA==:YTM3ZTM3ODktY2VmZi00NTYzLWE5M2MtNjY3NGY5Mjk1YmY1",
        "target": {
            "@id": "assetId"
        },
        "assigner": "provider",
        "permission": []
    }
}
```

The permission must be an empty array. If the array would have 1 Permission EDC will send a single Object not an Array of one (we support only array).

Finally, send the Verify negotiation request as is.

### TC provider - EDC consumer

To start the contract negotiation TC provider - EDC consumer start with the EDC request Negotiate contract and insert following body:

```
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "ContractRequest",
  "counterPartyAddress": "http://localhost:8090",
  "protocol": "dataspace-protocol-http",
  "policy": {
    "@context": "http://www.w3.org/ns/odrl.jsonld",
    "@id": "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5",
    "@type": "Offer",
    "assigner": "TC-provider",
    "target": "assetId"
  }
}
```

Afterwards use Find Contract Negotiation, Approve negotiation and Finalize negotiation requests from TC in that order, no changes needed.

## Notes

During the testing and integration we have noticed the following:

- dpsace value "https://w3id.org/dspace/v0.8/" instead "https://w3id.org/dspace/1/0/context.json"
- Constraint.leftOperand, Constraint.operator, Permission.target, Permission.action, ContractNegotiationEventMessage.eventType are now objects
(they can be String or as Reference, json object with @id as key and String for value)
 
```
				"odrl:leftOperand": {
					"@id": "odrl:count"
				},
				"odrl:operator": {
					"@id": "odrl:eq"
				}
				 
				"odrl:target": {
					"@id": "assetId"
				}
```

- dct:format - "HttpData-PULL" and "HttpData-PUSH"
- ContractNegotiationConsumerService - callbackAddress might be omitted in ContractAgreementMessage, in that case we will use initial "Forward-To" as callbackAddress