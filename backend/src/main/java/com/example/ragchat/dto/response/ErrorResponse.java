package com.example.ragchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String error;
    private String message;
    private List<String> details;
    private String path;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static ErrorResponse of(String error, String message) {
        return ErrorResponse.builder()
                .error(error)
                .message(message)
                .build();
    }

    public static ErrorResponse of(String error, String message, List<String> details) {
        return ErrorResponse.builder()
                .error(error)
                .message(message)
                .details(details)
                .build();
    }
}
