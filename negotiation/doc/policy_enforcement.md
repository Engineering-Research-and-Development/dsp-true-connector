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
 - make use of newly created module in negotiation module, to call logic of policy enforcement class (avoid making rest endpoint?)
 
 
 