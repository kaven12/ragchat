package com.example.ragchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {

    private String accessToken;
    private Long expiresIn;

    public static RefreshResponse of(String accessToken, Long expiresIn) {
        return RefreshResponse.builder()
                .accessToken(accessToken)
                .expiresIn(expiresIn)
                .build();
    }
}
