package it.eng.tools.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic filter builder with STRICT TYPING to eliminate ambiguity.
 * 
 * Type Conversion Rules:
 * - Boolean: Only "true", "false", "yes", "no" (case insensitive) 
 * - DateTime: ISO formats, YYYY-MM-DD dates, Unix timestamps (10-13 digits)
 * - Number: Integers, decimals, scientific notation (excludes potential timestamps)
 * - String: Everything else that doesn't match above patterns
 * 
 * Ambiguity Resolution:
 * - "1", "0" → Numbers (not booleans)
 * - "1234567890123" → DateTime timestamp (not number)
 * - Invalid formats → Rejected (null) rather than fallback to string
 */
@Component
@Slf4j
public class GenericFilterBuilder {
    
    private static final int MAX_PARAMETERS = 50;
    private static final Set<String> EXCLUDED_PARAMETERS = Set.of("page", "size", "sort", "_", "timestamp");
    private static final Set<String> NULL_REPRESENTATIONS = Set.of("null", "undefined", "nil", "none");
    private static final String[] SUSPICIOUS_PATTERNS = {
        "$where", "javascript:", "<script", "drop table", "insert into", "delete from",
        "update set", "create table", "alter table", "exec", "union select"
    };
    
    /**
     * Build filter map from HttpServletRequest with comprehensive edge case handling.
     * 
     * @param request the HTTP servlet request containing query parameters
     * @return a map of validated and converted filter parameters
     */
    public Map<String, Object> buildFromRequest(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        
        // Limit number of parameters to prevent abuse
        if (parameterMap.size() > MAX_PARAMETERS) {
            log.warn("Too many parameters provided: {}", parameterMap.size());
            throw new IllegalArgumentException("Maximum " + MAX_PARAMETERS + " filter parameters allowed");
        }
        
        Map<String, Object> filters = new HashMap<>();
        
        parameterMap.forEach((paramName, paramValues) -> {
            if (isValidFieldName(paramName) && isValidParameter(paramName, paramValues)) {
                Object convertedValue = handleMultipleValues(paramValues);
                if (isValidValue(convertedValue)) {
                    filters.put(paramName, convertedValue);
                }
            }
        });
        
        return filters;
    }
    
    private boolean isValidFieldName(String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return false;
        }
        
        // MongoDB reserved operators and special fields
        if (fieldName.startsWith("$") || fieldName.equals("_id")) {
            log.warn("Reserved MongoDB field name: {}", fieldName);
            return false;
        }
        
        // Handle nested field syntax but validate each part
        String[] parts = fieldName.split("\\.");
        for (String part : parts) {
            if (part.isEmpty() || part.startsWith("$")) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isValidParameter(String paramName, String[] paramValues) {
        return !isExcludedParameter(paramName) &&
               paramValues != null && 
               paramValues.length > 0;
    }
    
    private boolean isExcludedParameter(String paramName) {
        return EXCLUDED_PARAMETERS.contains(paramName);
    }
    
    private Object handleMultipleValues(String[] paramValues) {
        if (paramValues.length == 1) {
            return convertByType(paramValues[0]);
        }
        
        // Multiple values → convert to list for IN query
        List<Object> convertedValues = Arrays.stream(paramValues)
            .map(this::convertByType)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        return convertedValues.isEmpty() ? null : convertedValues;
    }
    
    private boolean isValidValue(Object value) {
        if (value == null) return false;
        
        if (value instanceof String) {
            String strValue = (String) value;
            String trimmed = strValue.trim();
            
            // Filter out empty/whitespace
            if (trimmed.isEmpty()) return false;
            
            // Filter out common null representations
            if (NULL_REPRESENTATIONS.contains(trimmed.toLowerCase())) {
                return false;
            }
            
            // Basic security check
            if (containsSuspiciousPatterns(trimmed)) {
                log.warn("Suspicious input detected and rejected: {}", trimmed);
                return false;
            }
        }
        
        if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    private boolean containsSuspiciousPatterns(String value) {
        String lower = value.toLowerCase();
        return Arrays.stream(SUSPICIOUS_PATTERNS)
            .anyMatch(lower::contains);
    }
    
    /**
     * Convert value based on detected type with comprehensive edge case handling.
     * 
     * @param value the string value to convert
     * @return the converted value in appropriate type, or null if conversion fails
     */
    private Object convertByType(String value) {
        if (value == null) return null;
        
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) return null;
        
        // Try boolean conversion FIRST (handles "1", "0", "true", "false", etc.)
        Object converted = tryConvertToBoolean(trimmedValue);
        if (converted != null) return converted;
        
        // Check if value looks like a date/time - if so, it must convert successfully or be rejected
        if (looksLikeDateTime(trimmedValue)) {
            return tryConvertToDateTime(trimmedValue); // null if invalid
        }
        
        // Check if value looks like a number - if so, it must convert successfully or be rejected  
        if (looksLikeNumber(trimmedValue)) {
            return tryConvertToNumber(trimmedValue); // null if invalid
        }
        
        return trimmedValue; // Valid string
    }
    
    private boolean looksLikeDateTime(String value) {
        // STRICT: Only unambiguous datetime formats
        
        // ISO format with timezone (must have T and Z or +/- timezone)
        if (value.contains("T") && (value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$"))) {
            return true;
        }
        
        // Date only format (strict: exactly YYYY-MM-DD)
        if (value.matches("\\d{4}-\\d{2}-\\d{2}$")) {
            return true;
        }
        
        // Unix timestamp (strict: 10-13 digits only, no other numeric patterns)
        if (value.matches("\\d{10,13}$")) {
            return true;
        }
        
        return false;
    }
    
    private boolean looksLikeNumber(String value) {
        // STRICT: Only unambiguous numeric formats
        
        // Reject special cases that should not be treated as numbers
        String lower = value.toLowerCase();
        if (Set.of("infinity", "-infinity", "nan").contains(lower)) {
            return true; // These look like numbers but will be rejected
        }
        
        // Scientific notation (strict: must have e/E with exponent)
        if (value.matches("-?\\d+(\\.\\d+)?[eE][+-]?\\d+$")) {
            return true;
        }
        
        // Integer pattern (strict: digits only, excluding timestamps which are handled by datetime)
        if (value.matches("-?\\d+$")) {
            // Exclude unix timestamps (10-13 digits) - those are dates
            int digitCount = value.replaceAll("-", "").length();
            if (digitCount >= 10 && digitCount <= 13) {
                return false; // This should be handled as datetime
            }
            return true;
        }
        
        // Decimal pattern (strict: must have decimal point with digits on at least one side)
        if (value.matches("-?\\d+\\.\\d+$") || value.matches("-?\\d+\\.$") || value.matches("-?\\.\\d+$")) {
            return true;
        }
        
        return false;
    }
    
    private Object tryConvertToDateTime(String value) {
        try {
            // STRICT: Only convert values that passed the looksLikeDateTime check
            
            // Reject extreme/invalid years upfront
            if (value.startsWith("0000") || value.startsWith("9999")) {
                log.debug("Extreme year detected: {}", value);
                return null;
            }
            
            // ISO format with timezone (strict validation)
            if (value.contains("T") && (value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$"))) {
                Instant instant = Instant.parse(value);
                
                // Strict range validation (1970-2100)
                if (instant.isBefore(Instant.EPOCH) || 
                    instant.isAfter(Instant.parse("2100-01-01T00:00:00Z"))) {
                    log.debug("Date outside valid range (1970-2100): {}", value);
                    return null;
                }
                return instant;
            }
            
            // Date only format (strict: YYYY-MM-DD)
            if (value.matches("\\d{4}-\\d{2}-\\d{2}$")) {
                LocalDate date = LocalDate.parse(value);
                
                // Strict range validation
                if (date.getYear() < 1970 || date.getYear() > 2100) {
                    log.debug("Date year outside valid range: {}", value);
                    return null;
                }
                
                // Additional validation: ensure it's a valid date
                if (date.getMonthValue() < 1 || date.getMonthValue() > 12 ||
                    date.getDayOfMonth() < 1 || date.getDayOfMonth() > 31) {
                    log.debug("Invalid date components: {}", value);
                    return null;
                }
                
                return date.atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            
            // Unix timestamp (strict: 10-13 digits)
            if (value.matches("\\d{10,13}$")) {
                long timestamp = Long.parseLong(value);
                
                // Strict timestamp bounds validation
                if (timestamp < 0 || timestamp > 4102444800000L) { // 2100-01-01 in milliseconds
                    log.debug("Timestamp outside valid range: {}", value);
                    return null;
                }
                
                // Convert based on magnitude (seconds vs milliseconds)
                return timestamp > 9999999999L ? 
                    Instant.ofEpochMilli(timestamp) : 
                    Instant.ofEpochSecond(timestamp);
            }
            
        } catch (DateTimeException e) {
            log.debug("Date parsing failed: {} - {}", value, e.getMessage());
        } catch (NumberFormatException e) {
            log.debug("Timestamp parsing failed: {} - {}", value, e.getMessage());
        } catch (Exception e) {
            log.debug("Unexpected error parsing date: {} - {}", value, e.getMessage());
        }
        
        return null; // Failed all strict validations
    }
    
    private Object tryConvertToBoolean(String value) {
        try {
            String lower = value.toLowerCase();
            // Only convert unambiguous boolean values (exclude "1" and "0" to avoid number conflict)
            if (Set.of("true", "false", "yes", "no").contains(lower)) {
                return Set.of("true", "yes").contains(lower);
            }
        } catch (Exception e) {
            log.debug("Boolean parsing error: {}", value);
        }
        return null;
    }
    
    private Object tryConvertToNumber(String value) {
        try {
            // STRICT: Only convert values that passed the looksLikeNumber check
            
            // Reject special numeric values that should not be allowed
            String lower = value.toLowerCase();
            if (Set.of("infinity", "-infinity", "nan").contains(lower)) {
                log.debug("Rejecting special numeric value: {}", value);
                return null;
            }
            
            // Scientific notation (strict: must match exact pattern)
            if (value.matches("-?\\d+(\\.\\d+)?[eE][+-]?\\d+$")) {
                double d = Double.parseDouble(value);
                if (!Double.isFinite(d)) {
                    log.debug("Scientific notation resulted in non-finite value: {}", value);
                    return null;
                }
                return d;
            }
            
            // Integer pattern (strict: digits only, with overflow protection)
            if (value.matches("-?\\d+$")) {
                // Strict length check to prevent overflow
                String digits = value.replaceAll("-", "");
                if (digits.length() > 18) { // Long.MAX_VALUE has 19 digits, be conservative
                    log.debug("Integer too large, potential overflow: {}", value);
                    return null;
                }
                
                // Additional check: ensure it's within Long range
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    log.debug("Integer overflow detected: {}", value);
                    return null;
                }
            }
            
            // Decimal pattern (strict: must have proper decimal format)
            if (value.matches("-?\\d+\\.\\d+$") || value.matches("-?\\d+\\.$") || value.matches("-?\\.\\d+$")) {
                double d = Double.parseDouble(value);
                if (!Double.isFinite(d)) {
                    log.debug("Decimal resulted in non-finite value: {}", value);
                    return null;
                }
                // Additional check: ensure reasonable range for doubles
                if (Math.abs(d) > 1e100) {
                    log.debug("Decimal value too large: {}", value);
                    return null;
                }
                return d;
            }
            
        } catch (NumberFormatException e) {
            log.debug("Number parsing failed: {} - {}", value, e.getMessage());
        } catch (Exception e) {
            log.debug("Unexpected error parsing number: {} - {}", value, e.getMessage());
        }
        
        return null; // Failed all strict validations
    }
} 