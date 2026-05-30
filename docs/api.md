# API

## Management Server

Base URL: `http://localhost:8080`

### Create Short Link

```http
POST /api/v1/short-links
Content-Type: application/json
```

```json
{
  "originalUrl": "https://example.com/articles/1",
  "customCode": "article1",
  "expiresAt": "2026-12-31T00:00:00Z"
}
```

`customCode`, `expiresAt`은 선택값이다.

Response: `201 Created`

```json
{
  "code": "article1",
  "originalUrl": "https://example.com/articles/1",
  "shortUrl": "http://localhost:8081/article1",
  "createdAt": "2026-05-31T00:00:00Z",
  "expiresAt": "2026-12-31T00:00:00Z",
  "active": true
}
```

### Get Short Link

```http
GET /api/v1/short-links/{code}
```

Response: `200 OK`

### Find Recent Short Links

```http
GET /api/v1/short-links?limit=50
```

Response: `200 OK`

## Redirect Server

Base URL: `http://localhost:8081`

### Redirect

```http
GET /{code}
```

Responses:

- `302 Found`: `Location` header에 원본 URL 포함
- `400 Bad Request`: 짧은 코드 형식 오류
- `404 Not Found`: 존재하지 않는 코드
- `410 Gone`: 만료 또는 비활성 URL
