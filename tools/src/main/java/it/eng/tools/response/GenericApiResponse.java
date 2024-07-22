package it.eng.tools.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

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
    //, pattern = "dd-MM-yyyy HH:mm:ss"
    @JsonFormat(shape = JsonFormat.Shape.STRING)
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
