package it.eng.negotiation.policy.model;

/**
 * Enum representing different types of policies.
 */
public enum PolicyType {

	 /**
     * Usage control policies limit how data can be used.
     */
    USAGE_CONTROL,
    
    /**
     * Access control policies limit who can access data.
     */
    ACCESS_CONTROL,
    
    /**
     * Temporal policies limit when data can be accessed.
     */
    TEMPORAL,
    
    /**
     * Spatial policies limit where data can be accessed.
     */
    SPATIAL,
    
    /**
     * Purpose policies limit why data can be accessed.
     */
    PURPOSE,
    
    /**
     * Access count, how many times data can be accessed.
     */
    COUNT;
	
}
