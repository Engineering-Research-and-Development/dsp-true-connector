# Json-ld compact

We need to perform json-ld compacting using following context:

```
"@context": "https://w3id.org/dspace/2024/1/context.json"
```

As result, output json structure will be in array format, dataset, distribution, accessService, permission, constraint... If such action is not performed, deserialization will not be done correct and requests will not be processed.

EDC catalog as json-ld:

```
{
    "@id": "64aff64c-4e30-44cc-ad2b-2f6c60c17bb6",
    "@type": "dcat:Catalog",
    "dcat:dataset": {
        "@id": "assetId",
        "@type": "dcat:Dataset",
        "odrl:hasPolicy": {
            "@id": "MQ==:YXNzZXRJZA==:YjUzZTgyZTQtZDRiYS00MTZhLTk4ZmMtY2I0NWUyMTVhOTQx",
            "@type": "odrl:Offer",
            "odrl:permission":          
            {
                "odrl:action": "odrl:use",
                "odrl:constraint": 
                    {
                        "odrl:leftOperand": "odrl:count",
                        "odrl:operator": "odrl:EQ",
                        "odrl:rightOperand": "5"
                    }
            },
            "odrl:prohibition": [],
            "odrl:obligation": []
        },
        "dcat:distribution": [
            {
                "@type": "dcat:Distribution",
                "dct:format": {
                    "@id": "HttpData-PULL"
                },
                "dcat:accessService": {
                    "@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
                    "@type": "dcat:DataService",
                    "dcat:endpointDescription": "dspace:connector",
                    "dcat:endpointUrl": "http://localhost:19194/protocol",
                    "dct:terms": "dspace:connector",
                    "dct:endpointUrl": "http://localhost:19194/protocol"
                }
            },
            {
                "@type": "dcat:Distribution",
                "dct:format": {
                    "@id": "HttpData-PUSH"
                },
                "dcat:accessService": {
                    "@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
                    "@type": "dcat:DataService",
                    "dcat:endpointDescription": "dspace:connector",
                    "dcat:endpointUrl": "http://localhost:19194/protocol",
                    "dct:terms": "dspace:connector",
                    "dct:endpointUrl": "http://localhost:19194/protocol"
                }
            }
        ],
        "name": "product description",
        "id": "assetId",
        "contenttype": "application/json"
    },
    "dcat:distribution": [],
    "dcat:service": {
        "@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
        "@type": "dcat:DataService",
        "dcat:endpointDescription": "dspace:connector",
        "dcat:endpointUrl": "http://localhost:19194/protocol",
        "dct:terms": "dspace:connector",
        "dct:endpointUrl": "http://localhost:19194/protocol"
    },
    "dspace:participantId": "providerChnaged",
    "participantId": "provider",
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
        "edc": "https://w3id.org/edc/v0.0.1/ns/",
        "dcat": "http://www.w3.org/ns/dcat#",
        "dct": "http://purl.org/dc/terms/",
        "odrl": "http://www.w3.org/ns/odrl/2/",
        "dspace": "https://w3id.org/dspace/v0.8/"
    }
}
```

And when such string is deserialized, exception will be thrown saying cannot deserialize object, expecting array.

## Current implementation

```
<dependency>
    <groupId>com.github.jsonld-java</groupId>
    <artifactId>jsonld-java</artifactId>
    <version>0.13.6</version>
</dependency>
```

Following library supports replacing existing context (EDC) with new value (protocol version) 

```
Map<String, Object> jsonObject = (Map<String, Object>) JsonUtils.fromString(input);
jsonObject.put(JsonLdConsts.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_2024_01_VALUE);

```
and then performing compacting.

```
Map<String, Object> inContext = new HashMap<>();
inContext.put(JsonLdConsts.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_2024_01_VALUE);

// need to set version since default one is 1.0
JsonLdOptions ldOpts = new JsonLdOptions();
ldOpts.setProcessingMode(JsonLdOptions.JSON_LD_1_1);
				
Map<String, Object> compact = JsonLdProcessor.compact(jsonObject, inContext, ldOpts);
String compactContent = JsonUtils.toString(compact);
```

Output result will be:

```
{
	"@id": "64aff64c-4e30-44cc-ad2b-2f6c60c17bb6",
	"@type": "dcat:Catalog",
	"dcat:dataset": [
		{
			"@id": "assetId",
			"@type": "dcat:Dataset",
			"dcat:distribution": [
				{
					"@type": "dcat:Distribution",
					"dct:format": {
						"@id": "HttpData-PULL"
					},
					"dcat:accessService": [
						{
							"@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
							"@type": "dcat:DataService",
							"dct:endpointUrl": "http://localhost:19194/protocol",
							"dct:terms": "dspace:connector",
							"dcat:endpointDescription": "dspace:connector",
							"dcat:endpointUrl": "http://localhost:19194/protocol"
						}
					]
				},
				{
					"@type": "dcat:Distribution",
					"dct:format": {
						"@id": "HttpData-PUSH"
					},
					"dcat:accessService": [
						{
							"@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
							"@type": "dcat:DataService",
							"dct:endpointUrl": "http://localhost:19194/protocol",
							"dct:terms": "dspace:connector",
							"dcat:endpointDescription": "dspace:connector",
							"dcat:endpointUrl": "http://localhost:19194/protocol"
						}
					]
				}
			],
			"odrl:hasPolicy": [
				{
					"@id": "MQ==:YXNzZXRJZA==:YjUzZTgyZTQtZDRiYS00MTZhLTk4ZmMtY2I0NWUyMTVhOTQx",
					"@type": "odrl:Offer",
					"odrl:obligation": [],
					"odrl:permission": [
						{
							"odrl:action": "odrl:use",
							"odrl:constraint": [
								{
									"odrl:leftOperand": "odrl:count",
									"odrl:operator": "odrl:EQ",
									"odrl:rightOperand": "5"
								}
							]
						}
					],
					"odrl:prohibition": []
				}
			]
		}
	],
	"dcat:distribution": [],
	"dcat:service": [
		{
			"@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
			"@type": "dcat:DataService",
			"dct:endpointUrl": "http://localhost:19194/protocol",
			"dct:terms": "dspace:connector",
			"dcat:endpointDescription": "dspace:connector",
			"dcat:endpointUrl": "http://localhost:19194/protocol"
		}
	],
	"dspace:participantId": "providerChnaged",
	"@context": "https://w3id.org/dspace/2024/1/context.json"
}

```

having dataset, distribution, service, policy, permission, constraint as arrays, even if single element is present.


## Titanium library - TODO

Replace *com.github.jsonld-java:jsonld-java* with following:

```
<dependency>
    <groupId>com.apicatalog</groupId>
    <artifactId>titanium-json-ld</artifactId>
    <version>1.4.1</version>
</dependency>
<dependency>
    <groupId>org.glassfish</groupId>
    <artifactId>jakarta.json</artifactId>
    <version>2.0.1</version>
</dependency>

```

Also code changes will have to be applied:

Compacting code

```
try(InputStream is = ...) {
	Document document = JsonDocument.of(is);
	CompactionApi compacted = JsonLd.compact(document, createTCContext());
	Catalog c = Serializer.deserializeProtocol(compacted.get().toString(), Catalog.class);
			}



private Document createTCContext() throws JsonLdError {
		String context = "{"
				+ "\"@context\":  \"https://w3id.org/dspace/2024/1/context.json\""
				+ "}";
		return JsonDocument.of(new ByteArrayInputStream(context3.getBytes()));
	}
```


Since initial context is different, output after compaction will not be parsable by Serializable and object deserialized will not be valid.

Closest to usable json-ld string is like following:

```
{
	"@id": "64aff64c-4e30-44cc-ad2b-2f6c60c17bb6",
	"@type": "dcat:Catalog",
	"dcat:dataset": [
		{
			"@id": "assetId",
			"@type": "dcat:Dataset",
			"odrl:hasPolicy": [
				{
					"@id": "MQ==:YXNzZXRJZA==:YjUzZTgyZTQtZDRiYS00MTZhLTk4ZmMtY2I0NWUyMTVhOTQx",
					"@type": "odrl:Offer",
					"odrl:permission": [
						{
							"http://www.w3.org/ns/odrl/2/action": "odrl:use",
							"odrl:constraint": [
								{
									"http://www.w3.org/ns/odrl/2/leftOperand": "odrl:count",
									"http://www.w3.org/ns/odrl/2/operator": "odrl:EQ",
									"odrl:rightOperand": "5"
								}
							]
						}
					],
					"odrl:prohibition": [],
					"odrl:obligation": []
				}
			],
			"dcat:distribution": [
				{
					"@type": "dcat:Distribution",
					"dct:format": {
						"@id": "HttpData-PULL"
					},
					"dcat:accessService": [
						{
							"@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
							"@type": "dcat:DataService",
							"http://www.w3.org/ns/dcat#endpointDescription": "dspace:connector",
							"dcat:endpointUrl": "http://localhost:19194/protocol",
							"dct:terms": "dspace:connector",
							"dct:endpointUrl": "http://localhost:19194/protocol"
						}
					]
				},
				{
					"@type": "dcat:Distribution",
					"dct:format": {
						"@id": "HttpData-PUSH"
					},
					"dcat:accessService": [
						{
							"@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
							"@type": "dcat:DataService",
							"http://www.w3.org/ns/dcat#endpointDescription": "dspace:connector",
							"dcat:endpointUrl": "http://localhost:19194/protocol",
							"dct:terms": "dspace:connector",
							"dct:endpointUrl": "http://localhost:19194/protocol"
						}
					]
				}
			],
			"https://w3id.org/edc/v0.0.1/ns/name": "product description",
			"https://w3id.org/edc/v0.0.1/ns/id": "assetId",
			"https://w3id.org/edc/v0.0.1/ns/contenttype": "application/json"
		}
	],
	"dcat:distribution": [],
	"dcat:service": [
		{
			"@id": "f54a58e1-ceb9-4335-b123-976c84a9a34a",
			"@type": "dcat:DataService",
			"http://www.w3.org/ns/dcat#endpointDescription": "dspace:connector",
			"dcat:endpointUrl": "http://localhost:19194/protocol",
			"dct:terms": "dspace:connector",
			"dct:endpointUrl": "http://localhost:19194/protocol"
		}
	],
	"https://w3id.org/dspace/v0.8/participantId": "providerChnaged",
	"https://w3id.org/edc/v0.0.1/ns/participantId": "provider",
	"@context": "https://w3id.org/dspace/2024/1/context.json"
}
```

Notice differences:

 - "http://www.w3.org/ns/dcat#endpointDescription" - not replaced correct
 - "https://w3id.org/dspace/v0.8/participantId" while it should be "https://w3id.org/dspace/2024/1/participantId",
 - "http://www.w3.org/ns/odrl/2/leftOperand" - not replaced correct
 - "http://www.w3.org/ns/odrl/2/operator" - not replaced correct
 Probably because in https://w3id.org/dspace/2024/1/context.json after being defined in odrl and dcat contexts (urls) they are overridden later in document 
 
 ```
"dcat:endpointDescription": { "@type": "xsd:anyURI" },
"dspace:participantId": { "@type": "@id" },
"odrl:leftOperand": { "@type": "@id" },
"odrl:operator": { "@type": "@id" },
```
