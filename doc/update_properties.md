# Update properties at runtime

## Current configuration

Connector uses property files (static) to read properties and based on its value, to configure components. This is the easies solution but with some limitations. If user wants to have different behavior, process is following:

 * stop connector
 * change value in property file
 * start connector
 
This is not "user friendly" approach, and will require that user has access to the file system, where property file is located, which can lead to potential security issues.

### Improvement for static files

Spring offers actuator end-point and functionality to update properties at runtime.
You can check available properties (read from property file) and its current values, by issuing GET request to:

*http://localhost:8080/actuator/env*

This endpoint might require authorization header to be present.

Once you get the list of properties, value can be changed by sending POST request like following:

```
curl --location 'http://localhost:8080/actuator/env' \
--header 'Content-Type: application/json' \
--header 'Authorization: Basic cGV0YXJAbWFpbC5jb206cGFzc3dvcmQ=' \
--data '{
    "name": "property.name",
    "value": "property.new.value"
}'
```

More user friendly approach is to build UI around logic for updating properties.

One downside for this approach is that when connector is restarted, it will pick up properties from property file - initial values, and not updated ones, so in order to have updated functionality, same request will have to be send again. Or to update property file itself.

### File Monitor Solution

Implement logic for monitoring property file and once property file is changed, Spring will reconfigure itself. This is something in between solution, but still reqires acess to the file system and property file itself.

Solution can be checked [here](https://github.com/blackjackyau/spring-config-file-based-auto-reloading/tree/master)

## Future improvements

There are several way to do this, which all of them have its pros and cons. 

### Properties from database

This solution will require relation database to be present, like MySQL, PostgreSQL, Oracle or any other. Database will have to be prepopulated with data (key-value pairs, and maybe some other fields, profile, environment) so that Spring has them during startup. 
Downside is that H2 in memory database cannot be used for this solution.

Same improvement, related with UI can be applied here, that part of the UI will be delegated for managing and updating properties. Once property is updated, Spring will reconfigure itself with new values. This way, changes will be persisted in DB and will be picked up on next startup.

In other hand, database will have to be created and populated with data before starting the connector. When new properties are introduced or removed, script for populating data will have to be updated, to keep the consistency.

This might have slight impact on testing the connector using automated tools.

Example can be checked in following [link](https://ankitwasankar.medium.com/load-spring-boot-app-properties-from-database-before-application-start-9af302d5dd54)

### Spring Cloud Config

Spring offers solution to externalize properties to GitHub or any other system, like relation database. This solution adds overall complexity to the project and adds one requirement - database must be present and prepopulated with data (not possible to use in memory database). 
Idea is to have separate service to handle database properties, with manageable UI and, which will start up first, expose endpoints for propagating properties and then to start connector, which will use URL to this service, to get properties and values to configure connector.

Some of the solutions are: [solution1](https://sahana-bhat.medium.com/spring-cloud-config-server-with-jdbc-backend-bdb03f2a37d0) [solution2](https://github.com/spring-cloud/spring-cloud-config/issues/1848) [solution3](https://www.devglan.com/spring-cloud/spring-cloud-config) [solution4](https://www.devglan.com/spring-cloud/jdbc-backend-spring-cloud-config)
