package it.eng.tools.s3.provision.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.ZonedDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class S3CopyBucketResponse<T> implements Serializable {

    private boolean success;
    private String message;
    private T data;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime timestamp;

    public static <T> S3CopyBucketResponse<T> success(T data, String message) {
        return S3CopyBucketResponse.<T>builder()
                .message(message)
                .success(true)
                .data(data)
                .timestamp(ZonedDateTime.now())
                .build();
    }

    public static <T> S3CopyBucketResponse<T> success(String message) {
        return S3CopyBucketResponse.<T>builder()
                .message(message)
                .success(true)
                .data(null)
                .timestamp(ZonedDateTime.now())
                .build();
    }

    public static <T> S3CopyBucketResponse<T> error(String message) {
        return S3CopyBucketResponse.<T>builder()
                .message(message)
                .success(false)
                .data(null)
                .timestamp(ZonedDateTime.now())
                .build();
    }

    public boolean succeeded() {
        return success;
    }
}
