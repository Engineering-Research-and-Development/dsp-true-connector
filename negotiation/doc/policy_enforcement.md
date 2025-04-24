# Policy Enforcement

## Implementation

### PolicyEnforcementPoint 

is entry point for policy evaluation. This class 

 - gathers additional information needed for policy evaluation by calling PolicyInformationPoint 
 - creates PolicyRequest by adding gathered information
 - passes PolicyRequest and agreement to PolicyDecisionPoint for enforcement
 
### PolicyDecisionPoint

 - convert agreement.permission[].constraint[] to Policy class
 - for each policyType (based on supported LeftOpernad) calls dedicated policyEvaluator to evaluate policy
 - if ALL policies are valid then agreement is valid (currently only 'and' operator for constraints/permissions is supported)

### PolicyInformationPoint

Logic for providing additional information needed for policy evaluation
 - accessTime ('now')
 - location (currently value from property file until verifiable credentials are implemented; then probably one of claims)
 - purpose (currently value from property file until verifiable credentials are implemented; then probably one of claims)
 - current access count

### PolicyAdministrationPoint

 - create policyEnforcement (store information for current access count)
 - update access count 
 - does policyEnforcement exists by agreementId
 
## Supported policies (constraints)

| Policy | Left Operand | Operators | Right Operand | Example |
| :---- | :---- | :---- | :---- | :---- |
| [Number of usages](../src/main/java/it/eng/negotiation/policy/evaluator/AccessCountPolicyEvaluator.java) | COUNT | LT,  LTEQ | Number (as String) | 5 |
| [Date time](../src/main/java/it/eng/negotiation/policy/evaluator/TemporalPolicyEvaluator.java) | DATE_TIME | LT, LTEQ, GT, GTEQ | Date time in UTC (as String) | 2024-02-29T00:00:01+01:00 |
| [Purpose](../src/main/java/it/eng/negotiation/policy/evaluator/PurposePolicyEvaluator.java) | PURPOSE | IS_ANY_OF, EQ | Purpose (as String) | demo (default value for property) |
| [Spatial](../src/main/java/it/eng/negotiation/policy/evaluator/SpatialPolicyEvaluator.java) | SPATIAL | IS_ANY_OF, EQ | Location (as String) | EU (current value in property) |

### Json examples

 - Number of usages

```
{
  "target" : "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "action" : "USE",
  "constraint" : [ {
    "leftOperand" : "COUNT",
    "operator" : "LTEQ",
    "rightOperand" : "5"
  } ]
}
```

 - DateTime
 
 ```
{
  "target" : "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "action" : "USE",
  "constraint" : [ {
    "leftOperand" : "DATE_TIME",
    "operator" : "GT",
    "rightOperand" : "2024-02-29T00:00:01+01:00"
  } ]
}
 ```
 
  - Purpose
 
 ```
{
  "odrl:target" : "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "odrl:action" : "odrl:use",
  "odrl:constraint" : [ {
    "odrl:leftOperand" : "odrl:purpose",
    "odrl:operator" : "odrl:EQ",
    "odrl:rightOperand" : "demo"
  } ]
}
 ```
 
   - Spatial
 
 ```
{
  "odrl:target" : "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5",
  "odrl:action" : "odrl:use",
  "odrl:constraint" : [ {
    "odrl:leftOperand" : "odrl:spatial",
    "odrl:operator" : "odrl:EQ",
    "odrl:rightOperand" : "EU"
  } ]
}
 ```

## Improvements (?)

 - consider creating job that will scan all agreements and check ones that are expired and possibly remove artifact that are no longer accessible on consumer side
 
 