# Profiles

Since DSP TRUEConnector is based on SpringBoot, it is naturally supporting Spring profiles.

Those profiles are used as follows:

## Locally running 2 instances (from IDE)

When running 2 instances of connector, simulating or testing interaction between "consumer" and "provider", you will have to pass Spring profile as parameter. Default supported profiles are *consumer* and *provider* and such values should be passed to SpringBoot application.

There are 2 local application property files, that relies on those 2 profiles:

 - application-consumer.properties
 - application-provider.properies
 
located in connector/src/main/resources folder.

Those 2 property files have different server ports, to avoid using same port and thus not being able to run 2 instances, 2 different Mongo connections.

Another important profile related file is initial_data.json file, used to populate Mongo database with connector metadata, users, properties and other information, required to distinguish between 2 running instances, to simulate "consumer" and "provider" connector. Same naming convention is used for initial_data.json file, and initial_data-consumer.json and initial_data-provider.json files are located in same directory.


## Maven build

Maven build is not using any Spring profile, and it requires application.property file in src/test/resources. initial_data.json file is also "without profile", with one small difference, that this json file is read from main resources folder.
 

## Containerized instance

When running connector as containerized, it is not necessary to pass Spring profile, while if required by setup, it can be done, simply by passing in environment section following variable:

```
environment:
      - "SPRING_PROFILES_ACTIVE={DESIRED_PROFILE}"
      
```

Make sure to replace *{DESIRED_PROFILE}* with concrete value. If this is done, then logic for naming application.property file and initial_data.json is applied (Spring profile constraint for property file)


