package com.example.ghcrdemo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 오류 응답을 표현하는 DTO.
 * 클라이언트에 전달할 에러 메시지, HTTP 상태 코드 및 타임스탬프를 포함한다.
 */
@Data
public class ErrorResponse {
    /**
     * 에러 설명 메시지
     */
    private String message;

    /**
     * HTTP 상태 코드 (예: 400, 500)
     */
    private int status;

    /**
     * 에러 발생 시각 (생성 시 자동 설정)
     */
    private LocalDateTime timestamp;

    /**
     * 메시지와 상태 코드를 받아 ErrorResponse를 생성한다.
     * 생성 시 타임스탬프는 현재 시간으로 설정된다.
     *
     * @param message 에러 메시지
     * @param status  HTTP 상태 코드
     */
    public ErrorResponse(String message, int status) {
        this.message = message;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }
}
