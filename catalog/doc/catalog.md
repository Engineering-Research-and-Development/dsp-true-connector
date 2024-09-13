# Catalog

Example catalog (from init_data)


```json
{
    "@context": "https://w3id.org/dspace/2024/1/context.json",
    "@id": "urn:uuid:1dc45797-3333-4955-8baf-ab7fd66ac4d5",
    "@type": "dcat:Catalog",
    "dct:title": "Testcatalog - TRUEConnector team information",
    "dct:description": [
        {
            "@value": "Sample catalog offering TRUEConnector team information",
            "@language": "en"
        }
    ],
    "dspace:participantId": "urn:example:DataProviderA",
    "dcat:keyword": [
        "Employee",
        "Information",
        "Test",
        "TRUEConnector team information"
    ],
    "dcat:dataset": [
        {
            "@context": "https://w3id.org/dspace/2024/1/context.json",
            "@id": "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
            "@type": "dcat:Dataset",
            "dct:title": "TRUEConnector team information dataset",
            "dct:description": [
                {
                    "@value": "Dataset offering TRUEConnector team information",
                    "@language": "en"
                }
            ],
            "dcat:keyword": [
                "Personal information",
                "Employee",
                "TRUEConnector team",
                "REST",
                "SFTP",
                "json",
                "pdf"
            ],
            "odrl:hasPolicy": [
                {
                    "@type": "odrl:Offer",
                    "@id": "urn:uuid:fdc45798-a123-4955-8baf-ab7fd66ac4d5",
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
                    ]
                }
            ],
            "dcat:distribution": [
                {
                    "@type": "dspace:Distribution",
                    "dct:format": {
                        "@id": "HTTP-pull"
                    },
                    "dcat:accessService": [
                        {
                            "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
                            "@type": "dcat:DataService",
                            "dcat:creator": "Engineering Informatica S.p.A.",
                            "dcat:endpointDescription": "dspace:connector",
                            "dcat:endpointURL": "http://localhost:8090/",
                            "dcat:identifier": "DSP TRUE Connector Unique identifier for testing",
                            "dcat:issued": "2024-04-23T18:26:00+02:00",
                            "dcat:keyword": [
                                "REST",
                                "SFTP",
                                "transfer"
                            ],
                            "dcat:theme": [
                                "dark theme",
                                "light theme"
                            ],
                            "dct:conformsTo": "conformsToSomething",
                            "dct:description": [
                                {
                                    "@value": "DSP TRUEConnector service offering team information",
                                    "@language": "en"
                                }
                            ],
                            "dct:modified": "2024-04-23T18:26:00+02:00",
                            "dct:title": "DSP TRUE Connector"
                        }
                    ],
                    "@id": "urn:uuid:1dc45797-pdff-4932-8baf-ab7fd66ql4d5",
                    "dcat:issued": "2024-04-23T18:26:00+02:00",
                    "dct:modified": "2024-04-23T18:26:00+02:00",
                    "dct:title": "PDF file"
                },
                {
                    "@type": "dspace:Distribution",
                    "dct:format": {
                        "@id": "HTTP-pull"
                    },
                    "dcat:accessService": [
                        {
                            "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
                            "@type": "dcat:DataService",
                            "dcat:creator": "Engineering Informatica S.p.A.",
                            "dcat:endpointDescription": "dspace:connector",
                            "dcat:endpointURL": "http://localhost:8090/",
                            "dcat:identifier": "DSP TRUE Connector Unique identifier for testing",
                            "dcat:issued": "2024-04-23T18:26:00+02:00",
                            "dcat:keyword": [
                                "REST",
                                "SFTP",
                                "transfer"
                            ],
                            "dcat:theme": [
                                "dark theme",
                                "light theme"
                            ],
                            "dct:conformsTo": "conformsToSomething",
                            "dct:description": [
                                {
                                    "@value": "DSP TRUEConnector service offering team information",
                                    "@language": "en"
                                }
                            ],
                            "dct:modified": "2024-04-23T18:26:00+02:00",
                            "dct:title": "DSP TRUE Connector"
                        }
                    ],
                    "@id": "urn:uuid:1dc45797-json-4932-8baf-ab7fd66ql4d5",
                    "dcat:issued": "2024-04-23T18:26:00+02:00",
                    "dct:modified": "2024-04-23T18:26:00+02:00",
                    "dct:title": "JSON file"
                }
            ],
            "dcat:creator": "Engineering Informatica S.p.A.",
            "dcat:identifier": "Unique identifier for test Dataset",
            "dcat:issued": "2024-04-23T18:26:00+02:00",
            "dcat:theme": [
                "dark theme",
                "light theme"
            ],
            "dct:conformsTo": "conformsToSomething",
            "dct:modified": "2024-04-23T18:26:00+02:00"
        }
    ],
    "dcat:distribution": [
        {
            "@type": "dspace:Distribution",
            "dct:format": {
                "@id": "HTTP-pull"
            },
            "dcat:accessService": [
                {
                    "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
                    "@type": "dcat:DataService",
                    "dcat:creator": "Engineering Informatica S.p.A.",
                    "dcat:endpointDescription": "dspace:connector",
                    "dcat:endpointURL": "http://localhost:8090/",
                    "dcat:identifier": "DSP TRUE Connector Unique identifier for testing",
                    "dcat:issued": "2024-04-23T18:26:00+02:00",
                    "dcat:keyword": [
                        "REST",
                        "SFTP",
                        "transfer"
                    ],
                    "dcat:theme": [
                        "dark theme",
                        "light theme"
                    ],
                    "dct:conformsTo": "conformsToSomething",
                    "dct:description": [
                        {
                            "@value": "DSP TRUEConnector service offering team information",
                            "@language": "en"
                        }
                    ],
                    "dct:modified": "2024-04-23T18:26:00+02:00",
                    "dct:title": "DSP TRUE Connector"
                }
            ],
            "@id": "urn:uuid:1dc45797-pdff-4932-8baf-ab7fd66ql4d5",
            "dcat:issued": "2024-04-23T18:26:00+02:00",
            "dct:modified": "2024-04-23T18:26:00+02:00",
            "dct:title": "PDF file"
        },
        {
            "@type": "dspace:Distribution",
            "dct:format": {
                "@id": "HTTP-pull"
            },
            "dcat:accessService": [
                {
                    "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
                    "@type": "dcat:DataService",
                    "dcat:creator": "Engineering Informatica S.p.A.",
                    "dcat:endpointDescription": "dspace:connector",
                    "dcat:endpointURL": "http://localhost:8090/",
                    "dcat:identifier": "DSP TRUE Connector Unique identifier for testing",
                    "dcat:issued": "2024-04-23T18:26:00+02:00",
                    "dcat:keyword": [
                        "REST",
                        "SFTP",
                        "transfer"
                    ],
                    "dcat:theme": [
                        "dark theme",
                        "light theme"
                    ],
                    "dct:conformsTo": "conformsToSomething",
                    "dct:description": [
                        {
                            "@value": "DSP TRUEConnector service offering team information",
                            "@language": "en"
                        }
                    ],
                    "dct:modified": "2024-04-23T18:26:00+02:00",
                    "dct:title": "DSP TRUE Connector"
                }
            ],
            "@id": "urn:uuid:1dc45797-json-4932-8baf-ab7fd66ql4d5",
            "dcat:issued": "2024-04-23T18:26:00+02:00",
            "dct:modified": "2024-04-23T18:26:00+02:00",
            "dct:title": "JSON file"
        }
    ],
    "dcat:service": [
        {
            "@id": "urn:uuid:1dc45797-4444-conn-8baf-ab7fd66ac4d5",
            "@type": "dcat:DataService",
            "dcat:creator": "Engineering Informatica S.p.A.",
            "dcat:endpointDescription": "dspace:connector",
            "dcat:endpointURL": "http://localhost:8090/",
            "dcat:identifier": "DSP TRUE Connector Unique identifier for testing",
            "dcat:issued": "2024-04-23T18:26:00+02:00",
            "dcat:keyword": [
                "REST",
                "SFTP",
                "transfer"
            ],
            "dcat:theme": [
                "dark theme",
                "light theme"
            ],
            "dct:conformsTo": "conformsToSomething",
            "dct:description": [
                {
                    "@value": "DSP TRUEConnector service offering team information",
                    "@language": "en"
                }
            ],
            "dct:modified": "2024-04-23T18:26:00+02:00",
            "dct:title": "DSP TRUE Connector"
        }
    ],
    "dcat:creator": "Engineering Informatica S.p.A.",
    "dcat:identifier": "Unique identifier for test Catalog",
    "dcat:issued": "2024-04-23T18:26:00+02:00",
    "dcat:theme": [
        "dark theme",
        "light theme"
    ],
    "dct:conformsTo": "conformsToSomething",
    "dct:modified": "2024-04-23T18:26:00+02:00",
    "foaf:homepage": "https://www.homepage.com/test"
}
```