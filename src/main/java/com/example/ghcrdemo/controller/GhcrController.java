package com.example.ghcrdemo.controller;

import com.example.ghcrdemo.dto.AuthRequest;
import com.example.ghcrdemo.dto.ErrorResponse;
import com.example.ghcrdemo.dto.GhcrCatalogResponse;
import com.example.ghcrdemo.dto.GhcrTagsResponse;
import com.example.ghcrdemo.service.GitHubPackagesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/ghcr")
@RequiredArgsConstructor
/*
  GHCR 관련 REST API를 제공하는 컨트롤러 클래스.
  인증 정보(`AuthRequest`)를 받아 패키지 목록 및 태그 정보를 조회한다.
 */
public class GhcrController {

    private final GitHubPackagesService gitHubPackagesService;

    /**
     * 인증 정보를 받아 사용자의 컨테이너 패키지 목록을 동기적으로 조회한다.
     *
     * @param authRequest 사용자 인증 정보 (username, token)
     * @return 성공 시 `GhcrCatalogResponse`, 실패 시 `ErrorResponse`를 포함한 ResponseEntity
     */
    @PostMapping("/repositories")
    public ResponseEntity<?> getCatalog(@RequestBody AuthRequest authRequest) {
        try {
            GhcrCatalogResponse response = gitHubPackagesService.getCatalog(authRequest);
            return ResponseEntity.ok(response);
        } catch (WebClientResponseException e) {
            log.error("GitHub API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(new ErrorResponse("GitHub API error: " + e.getMessage(), e.getStatusCode().value()));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Internal server error: " + e.getMessage(), 500));
        }
    }

    /**
     * 주어진 레포지토리에 대해 태그 목록을 동기적으로 조회한다.
     * `repository` 경로 변수는 "owner/name" 또는 "name" 형식을 허용한다.
     *
     * @param repository  조회할 레포지토리 식별자 ("owner/name" 또는 "name")
     * @param authRequest 사용자 인증 정보 (username, token)
     * @return 성공 시 `GhcrTagsResponse`, 실패 시 `ErrorResponse`를 포함한 ResponseEntity
     */
    @PostMapping("/repositories/{repository}/tags")
    public ResponseEntity<?> getTags(@PathVariable String repository, @RequestBody AuthRequest authRequest) {
        try {
            GhcrTagsResponse response = gitHubPackagesService.getTags(repository, authRequest);
            return ResponseEntity.ok(response);
        } catch (WebClientResponseException e) {
            log.error("GHCR API error for repository {}: {} - {}", repository, e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(new ErrorResponse("GHCR API error: " + e.getMessage(), e.getStatusCode().value()));
        } catch (Exception e) {
            log.error("Unexpected error for repository {}: {}", repository, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Internal server error: " + e.getMessage(), 500));
        }
    }

    /**
     * 인증 정보를 받아 사용자의 컨테이너 패키지 목록을 비동기적으로 조회한다.
     *
     * @param authRequest 사용자 인증 정보 (username, token)
     * @return Mono\<ResponseEntity\<GhcrCatalogResponse\>\> 비동기 응답
     */
    @PostMapping("/async/repositories")
    public Mono<ResponseEntity<GhcrCatalogResponse>> getCatalogAsync(@RequestBody AuthRequest authRequest) {
        return gitHubPackagesService.getCatalogAsync(authRequest)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    /**
     * 주어진 레포지토리에 대해 태그 목록을 비동기적으로 조회한다.
     *
     * @param repository  조회할 레포지토리 식별자 ("owner/name" 또는 "name")
     * @param authRequest 사용자 인증 정보 (username, token)
     * @return Mono\<ResponseEntity\<GhcrTagsResponse\>\> 비동기 응답
     */
    @PostMapping("/async/repositories/{repository}/tags")
    public Mono<ResponseEntity<GhcrTagsResponse>> getTagsAsync(@PathVariable String repository, @RequestBody AuthRequest authRequest) {
        return gitHubPackagesService.getTagsAsync(repository, authRequest)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    /**
     * 간단한 헬스체크 엔드포인트.
     *
     * @return 상태 문자열
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("GHCR Demo API is running");
    }
}
