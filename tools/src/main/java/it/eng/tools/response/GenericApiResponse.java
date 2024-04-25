package it.eng.tools.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class GenericApiResponse<T> {
    private boolean success;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy hh:mm:ss")
    private LocalDateTime timestamp;
    private int httpStatus;


    //TODO Finish success after discussion with the team
    public static <T> GenericApiResponse<T> success(T data, String message, int httpStatus) {
        return GenericApiResponse.<T>builder()
                .message(message)
                .data(data)
                .success(true)
                .timestamp(LocalDateTime.now())
                .httpStatus(httpStatus)
                .build();
    }


    public static <T> GenericApiResponse<T> error(String message, int httpStatus) {
        return GenericApiResponse.<T>builder()
                .message(message)
                .success(false)
                .timestamp(LocalDateTime.now())
                .httpStatus(httpStatus)
                .build();
    }
}
