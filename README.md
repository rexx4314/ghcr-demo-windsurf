# GitHub Container Registry Demo API (GHCR)

> 이 프로젝트는 Windsurf AI에서 생성, GitHub Copilot으로 수정했습니다. (`ghcr-api-test.http` 제외)

Java 21 + Spring Boot 3.x + Gradle로 **GitHub Packages / GHCR 조회 API**를 구현한 데모입니다.  
요청마다 `username`/`token(PAT)`을 전달받아 **멀티유저**를 지원합니다.

---

## 기술 스택

- **Java**: 21 (LTS)
- **Spring Boot**: 3.2.0
- **Gradle**: 8.x
- **WebClient**: Spring WebFlux (리액티브 HTTP 클라이언트)
- **Jackson**: JSON 파싱
- **Lombok**: 보일러플레이트 제거

---

## 핵심 동작 방식 (서비스 코드 기준)

이 프로젝트는 “레포 목록”과 “태그 목록”을 **서로 다른 방식**으로 조회합니다.

### 1) 레포지토리(컨테이너 패키지) 목록 조회

- **GitHub REST API (Packages API)** 사용
- 호출: `GET https://api.github.com/users/{username}/packages?package_type=container`
- 인증: `Authorization: token {PAT}`
- 응답(배열)에서 `owner.login` + `name`을 추출해 `owner/name` 형태로 반환

### 2) 태그 목록 조회 (동기 / 비동기 방식이 다름)

#### ✅ 동기 태그 조회

- **GitHub REST API (Packages Versions API)** 사용
- 호출: `GET https://api.github.com/users/{owner}/packages/container/{packageName}/versions`
- 인증: `Authorization: token {PAT}`
- 각 version의 `metadata.container.tags[]`를 모아서 태그 목록 생성

> 즉, 동기 태그 조회는 `ghcr.io/v2/.../tags/list`를 **직접 호출하지 않습니다.**

#### ✅ 비동기 태그 조회

- **GHCR 토큰 발급 → ghcr.io/v2 태그 조회** 2단계로 진행

1) 토큰 발급  
   `GET https://ghcr.io/token?service=ghcr.io&scope=repository:{owner}/{repo}:pull`  
   인증: `Authorization: Basic base64(username:PAT)`
2) 태그 조회  
   `GET https://ghcr.io/v2/{owner}/{repo}/tags/list`  
   인증: `Authorization: Bearer {token}`

> 비동기 태그 조회는 실패 시 예외를 던지지 않고 **빈 tags 배열**을 반환하도록 처리되어 있습니다.

---

## 주요 기능

- 컨테이너 패키지(레포) 목록 조회
    - 동기/비동기 엔드포인트 제공
- 태그 목록 조회
    - 동기: GitHub Packages Versions API 기반(버전 메타데이터에서 tags 추출)
    - 비동기: GHCR Bearer 토큰 발급 후 `ghcr.io/v2`에서 tags/list 호출
- 멀티유저 지원 (요청마다 인증 정보 전달)
- 에러 로깅
    - 동기: 일부는 `.block()` 기준으로 실패 시 예외 가능
    - 비동기 태그: 실패 시 빈 결과 반환(복구 로직)

---

## 설정

### 1) 환경변수

요청마다 인증 정보를 받으므로 **환경변수 설정이 필요 없습니다.**

### 2) GitHub Personal Access Token (PAT) 생성

GitHub Settings → Developer settings → Personal access tokens → **Tokens (classic)**

권장(조회 전용):

- ✅ `read:packages` (GitHub Packages 조회)
- (프라이빗 리포지토리/패키지 접근이 필요하면) ✅ `repo`

> 이 프로젝트는 “조회”만 수행하므로 일반적으로 `write:packages`, `delete:packages`는 필요하지 않습니다.  
> 다만, 조직/권한/패키지 설정에 따라 `repo`가 필요할 수 있습니다(프라이빗 접근 등).

---

## 실행

```bash
./gradlew bootRun

# 또는
./gradlew build
java -jar build/libs/ghcr-demo-0.0.1-SNAPSHOT.jar
````

---

## API 엔드포인트

> 모든 API는 요청 바디로 `username`, `token`을 받습니다.

### 1) 컨테이너 패키지(레포) 목록 조회

```bash
# 동기
curl -X POST http://localhost:19090/api/ghcr/repositories \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_github_username",
    "token": "your_personal_access_token"
  }'

# 비동기
curl -X POST http://localhost:19090/api/ghcr/async/repositories \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_github_username",
    "token": "your_personal_access_token"
  }'
```

### 2) 특정 레포지토리 태그 목록 조회

> `{repository}`는 `"owner/name"` 또는 `"name"` 형태를 허용합니다.
> `"name"`만 주면 owner는 요청의 `username`으로 처리됩니다.

```bash
# 동기 (GitHub REST API에서 version metadata의 tags를 추출)
curl -X POST http://localhost:19090/api/ghcr/repositories/{username}/{repository}/tags \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_github_username",
    "token": "your_personal_access_token"
  }'

# 비동기 (GHCR Bearer 토큰 발급 후 ghcr.io/v2 tags/list 호출)
curl -X POST http://localhost:19090/api/ghcr/async/repositories/{username}/{repository}/tags \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your_github_username",
    "token": "your_personal_access_token"
  }'
```

### 3) 헬스 체크

```bash
curl -X GET http://localhost:19090/api/ghcr/health
```

---

## 응답 예시

### 레포 목록

```json
{
  "repositories": [
    "username/my-app",
    "username/another-service",
    "username/utilities"
  ]
}
```

### 태그 목록

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

> 주의: 동기 태그 조회는 “버전 목록”에서 tags를 모두 모으므로
> 같은 태그가 여러 버전에 등장하면 **중복**이 생길 수 있습니다(현재 서비스 코드 기준).

### 에러 응답(예시)

```json
{
  "message": "GitHub API error: 401 Unauthorized",
  "status": 401,
  "timestamp": "2026-01-16T13:07:00"
}
```

---

## curl로 직접 API 호출 테스트 (서비스 코드와 동일한 방식)

### A) 레포(컨테이너 패키지) 목록: GitHub Packages API

```bash
curl -H "Authorization: token your_pat" \
     -H "Accept: application/vnd.github.v3+json" \
     "https://api.github.com/users/username/packages?package_type=container"
```

### B-1) 태그 목록(동기 방식): Packages Versions API에서 tags 추출

```bash
curl -H "Authorization: token your_pat" \
     -H "Accept: application/vnd.github.v3+json" \
     "https://api.github.com/users/OWNER/packages/container/PACKAGE_NAME/versions"
```

> 응답의 각 version 객체 안에 `metadata.container.tags` 배열이 포함될 수 있습니다.

### B-2) 태그 목록(비동기 방식): GHCR 토큰 발급 → ghcr.io/v2 tags/list

```bash
# 1) 토큰 발급 (Basic base64(username:pat))
BASIC=$(printf "username:your_pat" | base64)

curl -H "Authorization: Basic $BASIC" \
     "https://ghcr.io/token?service=ghcr.io&scope=repository:owner/repo:pull"

# 2) 발급된 token으로 tags 조회
curl -H "Authorization: Bearer YOUR_BEARER_TOKEN" \
     -H "Accept: application/json" \
     "https://ghcr.io/v2/owner/repo/tags/list"
```

---

## 프로젝트 구조

```
src/main/java/com/example/ghcrdemo/
├── GhcrDemoApplication.java
├── config/
│   ├── GhcrProperties.java
│   └── WebClientConfig.java
├── controller/
│   └── GhcrController.java
├── dto/
│   ├── AuthRequest.java
│   ├── GhcrCatalogResponse.java
│   ├── GhcrTagsResponse.java
│   └── ErrorResponse.java
└── service/
    └── GitHubPackagesService.java
```

---

## 구현 메모 / 주의사항 (운영 관점)

- **GitHub API Rate Limit** 존재
- 컨테이너 패키지 목록 / 버전 목록은 **페이지네이션** 가능
  - 현재 서비스 코드는 **1페이지 응답만 처리**
- 동기 태그 조회 특성
  - 버전 메타데이터(`metadata.container.tags`) 기반
  - 태그가 없거나 메타데이터 구조가 다른 경우 **tags가 비어 있을 수 있음**
  - **중복 태그 제거 미구현** (필요 시 `Set` 등으로 별도 처리 필요)
- 비동기 태그 조회 특성
  - GHCR 토큰 발급 → `ghcr.io/v2/.../tags/list` 호출
  - 실패 시 **예외 대신 빈 tags 배열 반환** (장애 허용 설계)
  - 실패를 그대로 노출해야 하는 정책이라면 **에러 반환 방식으로 수정 필요**
- **PAT(Personal Access Token)** 는 민감정보
  - 로그 출력 / 저장 / 외부 노출에 각별히 주의
