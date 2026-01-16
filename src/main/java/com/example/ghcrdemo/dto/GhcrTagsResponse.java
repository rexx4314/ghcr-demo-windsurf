package com.example.ghcrdemo.dto;

import lombok.Data;

import java.util.List;

/**
 * GHCR 레포지토리의 태그 응답을 표현하는 DTO.
 * 레포지토리 이름과 해당 레포지토리의 태그 목록을 보관한다.
 */
@Data
public class GhcrTagsResponse {
    /**
     * 레포지토리 이름 (예: "owner/name" 또는 "name")
     */
    private String name;

    /**
     * 해당 레포지토리의 태그 목록
     */
    private List<String> tags;
}
