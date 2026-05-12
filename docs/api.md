# API 문서

이 문서는 현재 구현된 첫 MVP 엽서 흐름 API를 기록한다.

## 공통 규칙

- Base path: `/api/v1`
- 응답 형식:

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

- 실패 응답 형식:

```json
{
  "success": false,
  "data": null,
  "message": "오류 메시지"
}
```

## 인증

현재 첫 MVP는 개발용 임시 인증 방식을 사용한다.

- 모든 엽서 API 요청에는 `X-User-Id` 헤더가 필요하다.
- `X-User-Id` 값은 `app_users.id`에 존재하는 사용자 ID여야 한다.
- 이 방식은 운영 인증이 아니며, Google, Kakao Talk, 이메일 로그인 연동 전까지의 임시 방식이다.

예:

```http
X-User-Id: 2
```

## 관련 테이블

- `app_users`
- `postcards`
- `postcard_deliveries`

## 엽서 작성

개발용 사용자 ID에 해당하는 사용자가 텍스트 엽서를 작성한다.

```http
POST /api/v1/postcards
Content-Type: application/json
X-User-Id: 1
```

### 요청

```json
{
  "content": "오늘 밤 공기가 조금 시원하네요."
}
```

### 검증 규칙

- `content`는 필수다.
- `content`는 공백만 입력할 수 없다.
- `content`는 500자 이내다.

### 성공 응답

```json
{
  "success": true,
  "data": {
    "id": 1,
    "content": "오늘 밤 공기가 조금 시원하네요.",
    "status": "DELIVERABLE",
    "createdAt": "2026-05-12T19:20:00Z"
  },
  "message": null
}
```

## 오늘의 엽서 배정

개발용 사용자 ID에 해당하는 사용자에게 오늘 받을 엽서를 하나 배정한다.

같은 사용자가 같은 날짜에 이미 배정받은 엽서가 있으면 기존 배정을 반환한다.

```http
POST /api/v1/postcards/today/delivery
X-User-Id: 2
```

### 성공 응답

```json
{
  "success": true,
  "data": {
    "deliveryId": 1,
    "postcardId": 1,
    "content": "오늘 밤 공기가 조금 시원하네요.",
    "deliveredDate": "2026-05-12",
    "deliveredAt": "2026-05-12T19:21:00Z"
  },
  "message": null
}
```

### 동작 규칙

- 자기 자신이 작성한 엽서는 배정하지 않는다.
- 같은 사용자는 같은 날짜에 중복 배정받지 않는다.
- 배정 가능한 엽서가 없으면 실패한다.

## 오늘의 엽서 조회

개발용 사용자 ID에 해당하는 사용자가 오늘 배정받은 엽서를 조회한다.

작성자 식별 정보는 응답하지 않는다.

```http
GET /api/v1/postcards/today
X-User-Id: 2
```

### 성공 응답

```json
{
  "success": true,
  "data": {
    "deliveryId": 1,
    "postcardId": 1,
    "content": "오늘 밤 공기가 조금 시원하네요.",
    "deliveredDate": "2026-05-12",
    "deliveredAt": "2026-05-12T19:21:00Z"
  },
  "message": null
}
```

## 주요 오류 응답

### 사용자 헤더 누락

```json
{
  "success": false,
  "data": null,
  "message": "사용자 정보를 확인할 수 없습니다."
}
```

### 사용자 ID 형식 오류

```json
{
  "success": false,
  "data": null,
  "message": "사용자 정보 형식이 올바르지 않습니다."
}
```

### 사용자 없음

```json
{
  "success": false,
  "data": null,
  "message": "사용자를 찾을 수 없습니다."
}
```

### 엽서 내용 누락

```json
{
  "success": false,
  "data": null,
  "message": "content: 엽서 내용을 입력해주세요."
}
```

### 엽서 내용 길이 초과

```json
{
  "success": false,
  "data": null,
  "message": "content: 엽서는 500자 이내로 적어주세요."
}
```

### 배정 가능한 엽서 없음

```json
{
  "success": false,
  "data": null,
  "message": "전달 가능한 엽서가 없습니다."
}
```

### 오늘 배정된 엽서 없음

```json
{
  "success": false,
  "data": null,
  "message": "오늘 배정된 엽서가 없습니다."
}
```

## 미구현 범위

- 운영 인증과 인가.
- Google, Kakao Talk, Apple 로그인.
- 사진 업로드와 presigned URL 발급.
- 엽서 저장, 반응, 신고, 차단.
- 관리자 검수 API.
- 푸시 알림.
