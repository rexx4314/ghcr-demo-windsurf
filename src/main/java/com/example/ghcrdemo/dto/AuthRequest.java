package com.example.ghcrdemo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 인증 요청을 표현하는 DTO.
 * 사용자명과 토큰을 포함하며 요청 바디로 전달되어 인증에 사용된다.
 */
@Data
public class AuthRequest {
    /**
     * 사용자명 (필수)
     */
    @NotBlank(message = "Username is required")
    private String username;

    /**
     * 개인 액세스 토큰 (필수)
     */
    @NotBlank(message = "Token is required")
    private String token;
}
