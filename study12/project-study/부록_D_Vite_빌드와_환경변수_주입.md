# 부록 D. Vite 빌드와 환경변수 주입

> 12장 Dockerfile의 `ARG VITE_API_BASE_URL=/api`가 실제로 어떻게 동작하는지, 빌드 산출물인 `dist/` 안에 환경변수가 어떻게 박히는지 본다.

## D.1 학습 목표

```text
Vite가 무엇이고 Webpack과 어떻게 다른지
→ vite build가 dist/에 무엇을 만드는지
→ import.meta.env와 process.env의 차이
→ ARG/ENV가 Dockerfile 안에서 어떻게 빌드 시 환경변수를 박는지
→ 운영 컨테이너에 환경변수를 또 박을 수 없는 이유
```

## D.2 Vite vs Webpack

### Webpack

```text
하나의 큰 bundle.js로 모든 코드를 묶음
→ entry point에서 시작해 import를 따라가며 의존성 그래프 작성
→ 변환·압축·트리쉐이킹을 거쳐서 하나의 큰 JS를 산출
→ 변경 시 의존하는 모든 모듈을 다시 평가
→ 큰 프로젝트에서 시작이 느려진다
```

### Vite

```text
개발 시 브라우저 native ESM을 그대로 사용
→ 파일을 변경해도 그 모듈만 다시 평가
→ 브라우저가 필요한 모듈을 직접 요청하므로 번들 묶기 작업이 없다
→ 빌드 시에는 Rollup 기반으로 최적화된 정적 파일을 산출
```

현재 프로젝트는 Vite를 사용한다.

```json
// package.json
"scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
}
```

## D.3 `vite.config.js` 원문

```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
})
```

### 라인별 주석

```js
import { defineConfig } from 'vite' // Vite 설정을 타입 안전하게 선언하는 helper를 가져온다.
import react from '@vitejs/plugin-react' // React JSX를 처리하는 Vite plugin을 가져온다.

export default defineConfig({ // Vite가 읽을 설정 객체를 정의해 내보낸다.
  plugins: [react()], // React plugin을 활성화. JSX → JS 변환, Fast Refresh 등을 처리한다.
})
```

이 설정은 아주 단순하다. 추가 옵션이 없는 이유는 이 프로젝트에서 Vite 기본값으로 충분하기 때문이다. 기본값으로 다음이 자동 처리된다.

```text
JSX 변환 (@vitejs/plugin-react)
CSS import
public/ 정적 파일을 dist/로 복사
TypeScript (이 프로젝트는 .jsx만 쓰지만 .ts도 처리 가능)
소스맵 생성
```

## D.4 `npm run build`가 만드는 것

빌드 결과:

```text
dist/
├── index.html
└── assets/
    ├── index-[hash].js       # 약 250KB
    ├── index-[hash].css      # 약 10KB
    └── react-vendor-[hash].js # 약 140KB
```

`index.html` 안에는 이렇게 되어 있다.

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Vite + React</title>
    <script type="module" crossorigin src="/assets/index-AbCdEf.js"></script>
    <link rel="stylesheet" crossorigin href="/assets/index-AbCdEf.css">
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>
```

핵심:
- `<script type="module">`: 브라우저가 ESM으로 직접 로드
- 파일명에 `[hash]`: 내용이 바뀌면 해시가 바뀌어 캐시 무효화
- `crossorigin`: CORS 정책을 따르도록 명시

## D.5 환경변수 주입의 두 시점

Vite는 환경변수를 **두 시점**에 구분해 처리한다.

| 시점 | 변수 prefix | 접근 방법 | 변경 시점 |
|---|---|---|---|
| **빌드 시점** | `VITE_` | `import.meta.env.VITE_*` | 빌드 시점에 JS에 박힘. 재빌드 필요 |
| 런타임 (브라우저) | 없음 | 동적 fetch만 가능 | 브라우저에서 동적 처리 |

### 빌드 시점 변수

```dockerfile
# Dockerfile
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
```

```js
// src/api/api.js
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
```

`vite build`가 실행되면 Vite는 `import.meta.env.VITE_API_BASE_URL`을 **빌드 시점의 실제 값으로 치환**한다. 그 결과 `dist/assets/index-AbCdEf.js` 안에는 다음과 같이 박힌다.

```js
const API_BASE_URL = "/api" ?? "http://localhost:8080";
// or
const API_BASE_URL = "http://prod-be-domain" ?? "http://localhost:8080";
```

즉 **빌드 시점의 환경변수 값이 JS 안에 리터럴로 들어간다**. 이게 핵심이다.

### `import.meta.env` vs `process.env`

```js
// 잘못된 사용 — 브라우저에서 process는 없다
const url = process.env.VITE_API_BASE_URL;  // ReferenceError

// 올바른 사용 — Vite가 빌드 시 치환
const url = import.meta.env.VITE_API_BASE_URL;
```

`process.env`는 Node.js 런타임의 전역이다. 브라우저에는 없다. Vite는 `import.meta.env`만 빌드 시점에 환경변수 값을 박는다. `process.env` 패턴을 쓰면 빌드된 코드에서 `process is not defined` 에러가 난다.

## D.6 Dockerfile의 ARG/ENV 흐름

```dockerfile
FROM node:24-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci
COPY . .

ARG VITE_API_BASE_URL=/api         # 1. 빌드 시점 변수
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}  # 2. 컨테이너 환경변수로도 등록
RUN npm run build
```

### ARG와 ENV의 차이

```text
ARG VITE_API_BASE_URL=/api
→ docker build --build-arg VITE_API_BASE_URL=... 로 외부에서 주입 가능
→ 컨테이너 안에서 RUN 명령이 실행될 때만 보임
→ 빌드가 끝나면 사라짐 (런타임에는 안 보임)

ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
→ 컨테이너 안의 환경변수로 영구 등록
→ 빌드 후에도 컨테이너에서 보이지만 브라우저에는 전달되지 않음
```

### 왜 ENV도 같이 쓰는가?

Vite는 빌드 시점 환경변수만 본다. 즉 ARG만 있어도 충분하다. 그런데 ENV를 같이 두는 이유는:

```text
빌드 로그에서 ENV 값을 확인 가능
→ docker inspect <image>로 확인
→ CI/CD에서 어떤 값으로 빌드됐는지 검증

일부 Vite plugin이 런타임 환경변수도 fallback으로 참조
→ Vite 기본은 아니지만 안전장치
```

## D.7 운영 컨테이너에서 환경변수 변경이 안 되는 이유

`/api/posts`로 박힌 JS 파일을 운영 컨테이너에서 `VITE_API_BASE_URL=...` 환경변수만 바꿔서 덮쓸 수 있을까? **불가능하다.**

```text
빌드 결과 JS:
  const API_BASE_URL = "/api" ?? "http://localhost:8080";

런타임에 API_BASE_URL을 바꾸려면?
  → /api를 다른 값으로 바꾸려면 새 JS 파일이 필요
  → 새 JS 파일을 만들려면 npm run build를 다시 실행
  → 즉 새 컨테이너 이미지 + 재배포가 필요
```

이게 **빌드 시점 주입과 런타임 주입의 결정적 차이**다. 프론트엔드 환경변수를 운영 중에 바꾸려면:

```text
1. .env 또는 docker build --build-arg로 VITE_API_BASE_URL 변경
2. docker build로 새 이미지 생성
3. 새 이미지를 GHCR에 push
4. blue-green 배포로 새 컨테이너로 트래픽 전환
```

## D.8 nginx가 정적 파일을 서빙하는 구조

Dockerfile 마지막 단계:

```dockerfile
FROM nginx:1.28-alpine AS runtime
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

`/usr/share/nginx/html`은 nginx의 기본 정적 파일 루트다. `dist/`의 내용이 거기로 복사된다. 그러면 nginx는:

```text
GET /index.html
→ /usr/share/nginx/html/index.html 반환

GET /assets/index-AbCdEf.js
→ /usr/share/nginx/html/assets/index-AbCdEf.js 반환

GET /api/... (12장 frontend nginx)
→ proxy_pass http://${BACKEND_UPSTREAM}/
```

## D.9 캐시 전략

`nginx/default.conf`:

```nginx
location /assets/ {
    try_files $uri =404;
    add_header Cache-Control "public, max-age=31536000, immutable";
}
```

`/assets/`로 시작하는 파일은 Vite가 해시를 붙여서 만든 번들이다. 해시가 파일명에 있으므로:

```text
v1: assets/index-AbCdEf.js
v2: assets/index-XyZ123.js (내용 바뀌면 해시도 바뀜)
```

같은 URL을 두 번 요청하는 일은 절대 없다. 즉 **1년 캐시해도 안전**하다. 새 빌드를 배포하면 파일명이 바뀌므로 브라우저는 새 파일을 받게 된다.

`index.html`은 캐시하지 않는다. 매 요청마다 서버에서 받아서 새 `index.html`이 가리키는 새 해시 JS를 다운로드한다.

## D.10 환경변수 디버깅 요령

### 빌드 시 박힌 값 확인

```bash
docker run --rm <image> sh -c 'grep -r "API_BASE_URL" /usr/share/nginx/html/assets/ | head -3'
```

`API_BASE_URL = "/api"` 문자열이 JS에 그대로 박혀 있다.

### 런타임에 환경변수가 안 먹는 이유

```bash
docker exec -it <container> sh
# echo $VITE_API_BASE_URL  → 빈 값
```

컨테이너 안에는 환경변수가 있을 수 있지만 **JS는 이미 빌드 시 박혔으므로** 그 값을 다시 읽지 않는다.

## D.11 `import.meta.env`에 들어가는 모든 값

`VITE_*` prefix를 가진 변수 외에 Vite는 다음을 자동으로 추가한다.

```js
{
    BASE_URL: "/",          // base 경로
    MODE: "production",     // "development" | "production" | "test"
    DEV: false,             // development 모드 여부
    PROD: true,             // production 모드 여부
    SSR: false,             // SSR 모드 여부
}
```

`MODE`와 `DEV`/`PROD`는 빌드 시점에 결정된다. 코드 분기용으로 쓸 수 있다.

```js
if (import.meta.env.DEV) {
    console.log("개발 모드");
}
```

빌드 후에는 `false`로 최적화되어 dead code가 된다.

## D.12 이해 확인

1. Vite가 빌드 시 `import.meta.env.VITE_API_BASE_URL`을 처리하는 방식과, 런타임 환경변수가 JS에 반영되지 않는 이유를 설명하라.
2. Dockerfile의 `ARG`와 `ENV`의 차이를 빌드 시점/런타임 기준으로 설명하라.
3. nginx가 `/assets/` 경로에 `max-age=31536000, immutable`을 설정해도 안전한 이유를 해시 파일명과 연결해 설명하라.
4. Vite가 `VITE_` prefix가 없는 환경변수를 JS에 노출하지 않는 정책이 갖는 보안적 의미를 답하라.
5. `index.html`은 캐시하지 않고, `assets/`만 1년 캐시하는 전략이 일관되게 동작하는 이유를 설명하라.
