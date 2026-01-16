# GitHub Container Registry Demo API

| 이 프로젝트는 Windsurf AI에서 생성, GitHub Copilot으로 수정했습니다. (ghcr-api-test.http 제외)

Java 21, Spring Boot 3.x, Gradle로 구현된 GitHub Container Registry 조회 API입니다.

## 기술 스택

- **Java**: 21 (LTS)
- **Spring Boot**: 3.2.0
- **Gradle**: 8.x
- **WebClient**: Spring WebFlux (비동기 HTTP 클라이언트)
- **Lombok**: 코드 생성

## 주요 기능

- GitHub Container Registry 레포지토리 목록 조회 (GitHub Packages API 사용)
- 특정 레포지토리의 태그 목록 조회 (GHCR API 사용)
- 동기/비동기 API 엔드포인트 제공
- 에러 처리 및 로깅
- 멀티유저 지원 (요청마다 인증 정보 전달)

## 설정

### 1. 환경변수 설정 (선택사항)

이제 API는 요청마다 인증 정보를 받으므로 환경변수 설정이 필요 없습니다.

### 2. GitHub Personal Access Token 생성

1. GitHub Settings → Developer settings → Personal access tokens → **Tokens (classic)**
2. **Scopes**: 
   - ✅ `repo` (Full control of private repositories)
   - ✅ `read:packages` (Download packages from GitHub Packages)
   - ✅ `write:packages` (Upload packages to GitHub Packages)
   - ✅ `delete:packages` (Delete packages from GitHub Packages)
3. 생성된 토큰 복사

## 실행

```bash
# Gradle Wrapper로 빌드 및 실행
./gradlew bootRun

# 또는 직접 빌드 후 실행
./gradlew build
java -jar build/libs/ghcr-demo-0.0.1-SNAPSHOT.jar
```

## API 엔드포인트

### 1. 레포지토리 목록 조회

```bash
# 동기 API (GitHub Packages API 사용)
curl -X POST http://localhost:19090/api/ghcr/repositories \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_github_username",
    "token": "your_personal_access_token"
  }'

# 비동기 API
curl -X POST http://localhost:19090/api/ghcr/async/repositories \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_github_username",
    "token": "your_personal_access_token"
  }'
```

### 2. 특정 레포지토리 태그 목록 조회

```bash
# 동기 API (GHCR API 사용)
curl -X POST http://localhost:19090/api/ghcr/repositories/{username}/{repository}/tags \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_github_username",
    "token": "your_personal_access_token"
  }'

# 비동기 API
curl -X POST http://localhost:19090/api/ghcr/async/repositories/{username}/{repository}/tags \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_github_username",
    "token": "your_personal_access_token"
  }'
```

### 3. 헬스 체크

```bash
curl -X GET http://localhost:19090/api/ghcr/health
```

## 응답 예시

### 레포지토리 목록 응답

```json
{
  "repositories": [
    "username/my-app",
    "username/another-service",
    "username/utilities"
  ]
}
```

### 태그 목록 응답

```json
{
  "name": "username/my-app",
  "tags": [
    "latest",
    "v1.0.0",
    "v1.1.0",
    "main"
  ]
}
```

### 에러 응답

```json
{
  "message": "GitHub API error: 401 Unauthorized",
  "status": 401,
  "timestamp": "2026-01-16T13:07:00"
}
```

## curl 테스트 예시

### 직접 GitHub Packages API 테스트

```bash
# Base64 인코딩 (PowerShell)
$token = "username:your_pat"
$base64Token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($token))

# 레포지토리 목록 조회
curl -H "Authorization: token your_pat" \
     -H "Accept: application/vnd.github.v3+json" \
     https://api.github.com/users/username/packages?package_type=container

# 특정 레포지토리 태그 조회
curl -H "Authorization: Basic $base64Token" \
     -H "Accept: application/json" \
     https://ghcr.io/v2/username/my-repository/tags/list
```

## 프로젝트 구조

```
src/main/java/com/example/ghcrdemo/
├── GhcrDemoApplication.java          # 메인 애플리케이션 클래스
├── config/
│   ├── GhcrProperties.java           # GHCR 설정 프로퍼티
│   └── WebClientConfig.java         # WebClient 설정
├── controller/
│   └── GhcrController.java          # REST 컨트롤러
├── dto/
│   ├── AuthRequest.java             # 인증 요청 DTO
│   ├── GhcrCatalogResponse.java     # 레포지토리 목록 응답 DTO
│   ├── GhcrTagsResponse.java        # 태그 목록 응답 DTO
│   └── ErrorResponse.java           # 에러 응답 DTO
└── service/
    ├── GhcrService.java            # GHCR API 서비스 (기존)
    └── GitHubPackagesService.java    # GitHub Packages API 서비스 (신규)
```

## 주요 변경사항

### 1. 멀티유저 지원
- 환경변수 기반 설정에서 요청마다 인증 정보 전달 방식으로 변경
- `AuthRequest` DTO를 통해 사용자명과 토큰을 파라미터로 수신

### 2. 하이브리드 API 접근 방식
- **레포지토리 목록**: GitHub Packages API 사용 (`/users/{username}/packages?package_type=container`)
- **태그 목록**: GitHub Container Registry API 사용 (`/{repository}/tags/list`)
- GitHub Container Registry의 `_catalog` 엔드포인트 제한 문제 해결

### 3. 인증 방식 분리
- **GitHub Packages API**: `Authorization: token {token}` 방식
- **GitHub Container Registry API**: `Authorization: Basic {encoded_credentials}` 방식

## 주의사항

- GitHub API는 시간당 요청 제한이 있음
- Personal Access Token은 보안에 주의하여 관리
- 프라이빗 레포지토리 접근 시 적절한 권한 필요
- GitHub Container Registry의 `_catalog` 엔드포인트는 제한적 접근 가능
- 대규모 레포지토리 목록은 페이지네이션 고려 필요
