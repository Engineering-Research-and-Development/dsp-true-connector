package it.eng.connector.util;

import it.eng.tools.response.GenericApiResponse;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Consolidated utility class for controller operations.
 * Combines response building and other controller-related utilities.
 */
public final class ControllerUtils {

    private ControllerUtils() {
    }

    /**
     * Creates a successful response with data.
     *
     * @param data    the response data
     * @param message the success message
     * @param <T>     the data type
     * @return ResponseEntity with success response
     */
    public static <T> ResponseEntity<GenericApiResponse<T>> success(T data, String message) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(data, message));
    }

    /**
     * Creates a successful response with data and default message.
     *
     * @param data the response data
     * @param <T>  the data type
     * @return ResponseEntity with success response
     */
    public static <T> ResponseEntity<GenericApiResponse<T>> success(T data) {
        return success(data, "Operation completed successfully");
    }

    /**
     * Creates a successful response with just a message.
     *
     * @param message the success message
     * @return ResponseEntity with success response
     */
    public static ResponseEntity<GenericApiResponse<String>> success(String message) {
        return success("success", message);
    }

    /**
     * Creates an error response.
     *
     * @param message the error message
     * @return ResponseEntity with error response
     */
    public static ResponseEntity<GenericApiResponse<String>> error(String message) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.error(message));
    }

    /**
     * Creates an unauthorized error response.
     *
     * @param message the error message
     * @return ResponseEntity with unauthorized error response
     */
    public static ResponseEntity<GenericApiResponse<String>> unauthorized(String message) {
        return ResponseEntity.status(401)
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.error(message));
    }


    /**
     * Parses sort parameters safely to avoid ArrayIndexOutOfBoundsException.
     *
     * @param sort             the sort parameter array
     * @param defaultField     the default field to sort by
     * @param defaultDirection the default sort direction
     * @return Sort object
     */
    public static Sort parseSortParameters(String[] sort, String defaultField, Sort.Direction defaultDirection) {
        Sort.Direction direction = defaultDirection;
        String sortField = defaultField;

        if (sort.length >= 1) {
            sortField = sort[0];
        }
        if (sort.length >= 2) {
            direction = sort[1].equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;
        }

        return Sort.by(direction, sortField);
    }

    /**
     * Parses sort parameters with default ASC direction.
     *
     * @param sort         the sort parameter array
     * @param defaultField the default field to sort by
     * @return Sort object
     */
    public static Sort parseSortParameters(String[] sort, String defaultField) {
        return parseSortParameters(sort, defaultField, Sort.Direction.ASC);
    }
}
