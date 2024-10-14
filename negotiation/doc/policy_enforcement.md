# Policy Enforcement

## Implementation

PolicyEnforcementService is entry point for policy evaluation. This class contains switch case statement based on LeftOperand, and calls dedicated evaluator for specific constraint.
Each constraint check should have separate class that will encapsulate logic for evaluating it. 

```java
private boolean validateConstraint(String agreementId, Constraint constraint) {
	boolean valid = false;
	switch (constraint.getLeftOperand()) {
	case COUNT:
		valid = countPolicyValidator.validateCount(agreementId, constraint);
		break;
	case DATE_TIME:
		valid = dateTimePolicyValidator.validateDateTime(constraint);
		break;
	default:
		log.warn("Constraint not supported {}", constraint.getLeftOperand().name());
		return false;
	}
	return valid;
}
```

## Supported policies

| Policy | Left Operand | Operators | Right Operand | Example |
| :---- | :---- | :---- | :---- | :---- |
| [Number of usages](../src/main/java/it/eng/negotiation/service/policy/validators/CountPolicyValidator.java) | COUNT | LT,  LTEQ | Number (as String) | 5 |
| [Date time](../src/main/java/it/eng/negotiation/service/policy/validators/DateTimePolicyValidator.java) | DATE_TIME | LT, GT | Date time in UTC (as String) | 2024-10-01T06:00:00Z |

In case of multiple constraints, all constraints must be evaluated as true for overall policy to be evaluated as true.


## Improvements (?)

 - create new maven submodule that will contain classes for policy evaluation
 - move Constraint, LeftOperand and Operator from negotiation module to newly created
 - update references in negotiation module to use classes from new module
 - create new API endpoint for obtaining locally stored artifact (on consumer side, as BSON in Mongo)
 - enforce policy validation when consumer requests artifact data
 - data transfer for requested artifact must to be in completed state for consumer being able to get artifact
 - consider creating job that will scan all agreements and check ones that are expired and possibly remove artifact that are no longer accessible on consumer side (deleting them from Mongo)
 
Note:
Data transfer will address only transfering data from provider to consumer. Once artifact is transfered on consumer side, newly created API endoint in `usage control` module will be used to get data.
 
 
 