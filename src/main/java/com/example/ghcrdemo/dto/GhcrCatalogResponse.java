package com.example.ghcrdemo.dto;

import lombok.Data;

import java.util.List;

/**
 * GHCR 컨테이너 레지스트리의 카탈로그 응답을 표현하는 DTO.
 * 레포지토리 이름 목록을 보관한다.
 */
@Data
public class GhcrCatalogResponse {
    /**
     * 레포지토리 이름 목록 (예: "owner/name" 또는 "name")
     */
    private List<String> repositories;
}
