package it.eng.tools.response;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GenericApiResponse<T> implements Serializable {
	
	private static final long serialVersionUID = -1433451249888939134L;

	private boolean success;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;
    // ISO datetime format, same like we use in serializers
    // XXX will format to 2024-11-18T14:51:32+02:00 time zone offset
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime timestamp;


    //TODO Finish success after discussion with the team
    public static <T> GenericApiResponse<T> success(T data, String message) {
        return GenericApiResponse.<T>builder()
                .message(message)
                .data(data)
                .success(true)
                .timestamp(ZonedDateTime.now())
                .build();
    }


    public static <T> GenericApiResponse<T> error(String message) {
        return GenericApiResponse.<T>builder()
                .message(message)
                .success(false)
                .timestamp(ZonedDateTime.now())
                .build();
    }
    
    public static <T> GenericApiResponse<T> error(T data, String message) {
        return GenericApiResponse.<T>builder()
                .message(message)
                .data(data)
                .success(false)
                .timestamp(ZonedDateTime.now())
                .build();
    }
}
