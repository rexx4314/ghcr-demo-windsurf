package com.example.ghcrdemo.service;

import com.example.ghcrdemo.dto.AuthRequest;
import com.example.ghcrdemo.dto.GhcrCatalogResponse;
import com.example.ghcrdemo.dto.GhcrTagsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * GitHub 패키지(및 GHCR) 관련 API 호출을 담당하는 서비스 클래스.
 * 동기 및 비동기 방식으로 레포지토리 목록(catalog)과 태그 목록(tags)을 조회하는 기능 제공.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubPackagesService {

    /**
     * JSON 파싱용 ObjectMapper 인스턴스.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GitHub API를 호출해 사용자의 컨테이너 패키지 목록을 동기적으로 반환한다.
     *
     * @param authRequest 인증 정보(username, token)
     * @return GhcrCatalogResponse 패키지 목록을 담은 응답 DTO
     */
    public GhcrCatalogResponse getCatalog(AuthRequest authRequest) {
        log.info("Fetching GitHub packages catalog for user: {}", authRequest.getUsername());

        WebClient authWebClient = createAuthenticatedWebClient(authRequest);

        String response = authWebClient.get()
                .uri("/users/{username}/packages?package_type=container", authRequest.getUsername())
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> log.error("Error fetching GitHub packages catalog: {}", error.getMessage()))
                .block();

        return parsePackagesResponse(response);
    }

    /**
     * 주어진 repository에 대해 태그 목록을 동기적으로 조회한다.
     * repository가 "owner/name" 형태가 아니면 authRequest의 username을 owner로 사용한다.
     *
     * @param repository  "owner/package" 또는 "package"
     * @param authRequest 인증 정보
     * @return GhcrTagsResponse 태그 목록 DTO
     */
    public GhcrTagsResponse getTags(String repository, AuthRequest authRequest) {
        log.info("Fetching tags for repository: {} for user: {}", repository, authRequest.getUsername());

        // ghcr.io/v2 대신 api.github.com을 사용하여 일관성을 유지합니다.
        WebClient restWebClient = createAuthenticatedWebClient(authRequest);

        // repository 이름에서 owner와 package_name 분리
        String[] parts = repository.split("/");
        String owner = parts.length > 1 ? parts[0] : authRequest.getUsername();
        String packageName = parts.length > 1 ? parts[1] : parts[0];

        // GitHub REST API: /users/{org}/packages/container/{package_name}/versions
        // 이 API는 해당 컨테이너의 모든 버전(태그 포함) 정보를 JSON으로 반환합니다.
        return restWebClient.get()
                .uri("/users/{owner}/packages/container/{packageName}/versions", owner, packageName)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> {
                    // 버전(metadata) 목록에서 태그를 추출
                    List<String> tags = new ArrayList<>();
                    if (jsonNode.isArray()) {
                        for (JsonNode version : jsonNode) {
                            JsonNode metadata = version.get("metadata");
                            if (metadata != null && metadata.has("container")) {
                                JsonNode containerNode = metadata.get("container");
                                if (containerNode != null) {
                                    JsonNode tagsNode = containerNode.get("tags");
                                    if (tagsNode != null && tagsNode.isArray()) {
                                        for (JsonNode t : tagsNode) {
                                            String tag = t.asText();
                                            tags.add(tag);
                                            log.debug("Found tag='{}' for {}/{} (version={})",
                                                    tag, owner, packageName,
                                                    version.has("id") ? version.get("id").asText() : "unknown");
                                        }
                                    } else {
                                        log.debug("No tags array in container metadata for {}/{} (version={})",
                                                owner, packageName,
                                                version.has("id") ? version.get("id").asText() : "unknown");
                                    }
                                } else {
                                    log.debug("No container metadata for {}/{} (version={})",
                                            owner, packageName,
                                            version.has("id") ? version.get("id").asText() : "unknown");
                                }
                            }
                        }
                    }
                    GhcrTagsResponse response = new GhcrTagsResponse();
                    response.setName(repository);
                    response.setTags(tags);
                    return response;
                })
                .block();
    }

    /**
     * 비동기 방식으로 사용자의 컨테이너 패키지 목록을 조회한다.
     *
     * @param authRequest 인증 정보
     * @return Mono\<GhcrCatalogResponse\> 비동기 응답
     */
    public Mono<GhcrCatalogResponse> getCatalogAsync(AuthRequest authRequest) {
        WebClient authWebClient = createAuthenticatedWebClient(authRequest);
        return authWebClient.get()
                .uri("/users/{username}/packages?package_type=container", authRequest.getUsername())
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> log.error("Error fetching GitHub packages catalog: {}", error.getMessage()))
                .map(this::parsePackagesResponse);
    }

    /**
     * 비동기 방식으로 repository의 태그를 조회한다.
     * GHCR에서 직접 Bearer 토큰을 받아 ghcr.io/v2 엔드포인트로 요청한다.
     *
     * @param repository  "owner/package" 또는 "package"
     * @param authRequest 인증 정보
     * @return Mono\<GhcrTagsResponse\> 비동기 응답
     */
    public Mono<GhcrTagsResponse> getTagsAsync(String repository, AuthRequest authRequest) {
        String repoToUse = repository.contains("/") ? repository : authRequest.getUsername() + "/" + repository;

        return getGhcrBearerTokenAsync(authRequest, repoToUse)
                .flatMap(bearerToken ->
                        WebClient.builder()
                                .baseUrl("https://ghcr.io/v2")
                                .defaultHeader("Authorization", "Bearer " + bearerToken)
                                .defaultHeader("Accept", "application/json")
                                .build()
                                .get()
                                .uri("/{repository}/tags/list", repoToUse)
                                .retrieve()
                                .bodyToMono(GhcrTagsResponse.class)
                                .doOnSuccess(response -> log.info("Tags fetched successfully for {}: {} tags", repoToUse,
                                        response.getTags() != null ? response.getTags().size() : 0))
                )
                .onErrorResume(error -> {
                    // 실패 시 빈 응답 반환
                    log.error("Failed to fetch tags for {}: {}", repoToUse, error.getMessage());
                    GhcrTagsResponse emptyResponse = new GhcrTagsResponse();
                    emptyResponse.setName(repoToUse);
                    emptyResponse.setTags(new ArrayList<>());
                    return Mono.just(emptyResponse);
                });
    }

    /**
     * API에서 받은 JSON 문자열을 파싱해 GhcrCatalogResponse로 변환한다.
     *
     * @param response JSON 문자열
     * @return GhcrCatalogResponse 파싱 결과
     */
    private GhcrCatalogResponse parsePackagesResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<String> repositories = new ArrayList<>();

            if (root != null && root.isArray()) {
                for (JsonNode packageNode : root) {
                    String packageName = packageNode.get("name").asText();
                    String owner = packageNode.get("owner").get("login").asText();
                    repositories.add(owner + "/" + packageName);
                }
            }

            GhcrCatalogResponse catalogResponse = new GhcrCatalogResponse();
            catalogResponse.setRepositories(repositories);
            return catalogResponse;
        } catch (Exception e) {
            log.error("Error parsing packages response: {}", e.getMessage(), e);
            GhcrCatalogResponse errorResponse = new GhcrCatalogResponse();
            errorResponse.setRepositories(new ArrayList<>());
            return errorResponse;
        }
    }

    /**
     * 인증 헤더(token)를 포함한 WebClient를 생성한다 (GitHub API용).
     *
     * @param authRequest 인증 정보
     * @return WebClient 구성된 클라이언트
     */
    private WebClient createAuthenticatedWebClient(AuthRequest authRequest) {
        return WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "token " + authRequest.getToken())
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    /**
     * GHCR에서 사용할 Bearer 토큰을 비동기 방식으로 발급받는다.
     * 내부적으로 getBearerTokenDirect를 호출한다.
     *
     * @param authRequest 인증 정보
     * @param repository  "owner/package"
     * @return Mono\<String\> 발급된 토큰
     */
    private Mono<String> getGhcrBearerTokenAsync(AuthRequest authRequest, String repository) {
        String repoScope = "repository:" + repository + ":pull";
        return getBearerTokenDirect(authRequest, repoScope)
                .doOnSuccess(token -> log.debug("GHCR Bearer token obtained for {} (len={})", repository, token.length()))
                .doOnError(e -> log.error("Bearer token failed for {}: {}", repository, e.getMessage()));
    }

    /**
     * GHCR 토큰 엔드포인트를 직접 호출해 Bearer 토큰을 받아온다.
     *
     * @param authRequest 인증 정보
     * @param scope       요청할 scope (예: repository:owner/repo:pull)
     * @return Mono\<String\> 토큰 문자열
     */
    private Mono<String> getBearerTokenDirect(AuthRequest authRequest, String scope) {
        // GitHub 토큰 서비스 직접 호출 (fallback)
        String encodedToken = java.util.Base64.getEncoder()
                .encodeToString((authRequest.getUsername() + ":" + authRequest.getToken()).getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl("https://ghcr.io/token")
                .defaultHeader("Authorization", "Basic " + encodedToken)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("service", "ghcr.io")
                        .queryParam("scope", scope)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(jsonNode -> jsonNode.get("token").asText());
    }
}
