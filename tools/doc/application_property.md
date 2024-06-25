### Properties from database

This solution needs Mongo database to be present. Database is prepopulated with data (key-value pairs, and some other fields) or the properties value are added at startup. This happenes using two different ways. The first one is between initial_data.json file. The other one consists in an automatic mechanism during which a configuration class search the new application properties on environment and add them in Mongo. This second approach should avoid problems if new properties are introduced. They will be added automatically on MongoDb if they are present in application.properties file. 

Same improvement, related with UI can be applied here, that part of the UI will be delegated for managing and updating properties. Once property is updated, Spring will reconfigure itself with new values. This way, changes will be persisted in DB and will be picked up on next startup.

### API

### Get All Application Properties:

Request

```
curl --location 'http://localhost:8080/api/connector_property/' \
--header 'Content-Type: application/json' \
--header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ='
```

Response

```
[
    {
        "key": "spring.application.name",
        "value": "connector",
        "mandatory": false,
        "issued": 1719309173.524000000,
        "modified": 1719309173.524000000,
        "type": "ApplicationProperty"
    },
    {
        "key": "application.automatic.negotiation",
        "value": "false",
        "mandatory": false,
        "issued": 1719309173.586000000,
        "modified": 1719309173.586000000,
        "type": "ApplicationProperty"
    },
   ...
]
```

### Get All Application Properties with prefix:

Request

```
curl --location 'http://localhost:8080/api/connector_property/?key_prefix=application.daps' \
--header 'Content-Type: application/json' \
--header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ='
```

Response

```
[
    {
        "key": "application.daps.dapsJWKSUrl",
        "value": "https://daps.aisec.fraunhofer.de/.well-known/jwks.json",
        "mandatory": false,
        "issued": 1719246360.000000000,
        "modified": 1719246360.000000000,
        "type": "ApplicationProperty"
    },
    {
        "key": "application.daps.dapsUrl",
        "value": "https://daps.aisec.fraunhofer.de/v2/token",
        "mandatory": false,
        "issued": 1719246360.000000000,
        "modified": 1719246360.000000000,
        "type": "ApplicationProperty"
    },
    ...
]
```

### Get Application Property By Key:

Request

```
curl --location 'http://localhost:8080/api/connector_property/spring.ssl.bundle.jks.connector.keystore.location' \
--header 'Content-Type: application/json' \
--header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ='
```

Response

```
{
    "key": "spring.ssl.bundle.jks.connector.keystore.location",
    "value": "classpath:ssl-server.jks",
    "mandatory": false,
    "issued": 1719309173.784,
    "modified": 1719309173.784,
    "type": "ApplicationProperty"
}
```

### Update Application Property:

Request

```
curl --location --request PUT 'http://localhost:8080/api/connector_property/' \
--header 'Content-Type: application/json' \
--header 'Authorization: Basic YWRtaW5AbWFpbC5jb206cGFzc3dvcmQ=' \
--data '{
        "key": "application.daps.enabledDapsInteraction",
        "value": "false",
        "mandatory": false,
        "issued": 1718629303.981000000,
        "modified": 1718629303.981000000,
        "type": "ApplicationProperty"
    }'
```

Response

```
{
    "key": "application.daps.enabledDapsInteraction",
    "value": "false",
    "mandatory": false,
    "issued": 1.71924636E+9,
    "modified": 1719320995.0349747,
    "type": "ApplicationProperty"
}
```