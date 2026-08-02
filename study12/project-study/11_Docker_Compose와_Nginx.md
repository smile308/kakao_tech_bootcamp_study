# 11장. Docker, Compose와 Nginx

## 11.1 학습 목표

소스 코드가 Docker 이미지가 되고, Compose가 여러 컨테이너를 연결하며, Nginx가 사용자 요청을 활성 컨테이너로 전달하는 구조를 학습한다.

```text
Dockerfile
→ 한 프로그램의 이미지 제작법

Image
→ 실행에 필요한 파일과 환경의 묶음

Container
→ Image를 실제로 실행한 프로세스

Compose
→ 여러 Container의 환경·포트·네트워크·볼륨 관리

Nginx
→ 정적 파일 제공과 reverse proxy
```

## 11.2 실제 코드 원문: 백엔드 Dockerfile

```dockerfile
# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk AS build

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:26-jre AS runtime

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

위 블록은 실제 `Dockerfile` 원문 그대로다. 읽기 위한 줄별 설명은 뒤의 라인별 주석본에서 별도로 제공한다.

중요한 줄:

```dockerfile
FROM ... jdk AS build
# 컴파일 도구가 있는 JDK 단계에서 JAR를 만든다.

FROM ... jre AS runtime
# 실행에 필요한 JRE만 있는 작은 최종 이미지로 전환한다.

COPY --from=build ...
# build 단계의 결과 JAR만 runtime 단계로 복사한다.

USER spring:spring
# 애플리케이션을 root가 아닌 일반 사용자로 실행한다.
```

멀티 스테이지 빌드는 Gradle cache와 소스 파일 같은 빌드 재료를 최종 운영 이미지에 넣지 않는다.

## 11.3 실제 코드 원문: 프론트 Dockerfile

```dockerfile
# syntax=docker/dockerfile:1

FROM node:24-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./

RUN npm ci

COPY . .

ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

FROM nginx:1.28-alpine AS runtime

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

```text
Node build 단계
→ dependency 설치
→ Vite가 정적 dist 생성

Nginx runtime 단계
→ dist만 복사
→ HTML·JS·CSS 제공
```

`VITE_API_BASE_URL`은 Vite build 시 JavaScript 번들 안에 들어간다. 이미 만들어진 이미지의 런타임 환경변수만 바꿔서는 번들 값이 자동 변경되지 않는다.

## 11.4 컨테이너 내부 프론트 Nginx

실제 코드:

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    client_max_body_size 20m;

    location = /health {
        access_log off;
        default_type text/plain;
        return 200 "ok\n";
    }

    location /assets/ {
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

중요한 줄:

```nginx
try_files $uri $uri/ /index.html;
# /posts 같은 파일이 없어도 index.html을 반환하여 React Router가 URL을 처리하게 한다.

location /assets/
# 해시가 붙은 build asset을 장기 cache한다.

location = /health
# 프론트 컨테이너가 응답 가능한지 배포 스크립트가 확인한다.
```

## 11.5 실제 Compose 구조: 백엔드

```yaml
services:
  redis:
    image: redis:7.4-alpine
    volumes:
      - redis-data:/data
    networks:
      - backend-network

  backend-blue:
    image: ${BACKEND_IMAGE}:${BACKEND_BLUE_TAG}
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: ${DB_URL}
      REDIS_HOST: redis
    ports:
      - "127.0.0.1:${BACKEND_BLUE_PORT:-8081}:8080"
    networks:
      - backend-network

  backend-green:
    image: ${BACKEND_IMAGE}:${BACKEND_GREEN_TAG}
    ports:
      - "127.0.0.1:${BACKEND_GREEN_PORT:-8082}:8080"
    networks:
      - backend-network

  nginx:
    image: nginx:1.28-alpine
    ports:
      - "${BACKEND_NGINX_PORT:-80}:80"
    networks:
      - backend-network
```

위 코드는 실제 Compose에서 반복 환경변수와 health check를 제외한 축약본이다.

개념:

```text
service 이름 redis
→ 같은 Compose network의 backend가 REDIS_HOST=redis로 접근

127.0.0.1:8081:8080
→ EC2 외부에는 직접 공개하지 않고 host 내부 8081을 container 8080에 연결

Nginx :80
→ 외부 요청을 받는 단일 입구
```

## 11.6 실제 Nginx blue 설정

```nginx
upstream active_backend {
    server backend-blue:8080;
}

server {
    listen 80;
    server_name _;

    location = /health {
        proxy_pass http://active_backend/actuator/health;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://active_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

green 설정은 `backend-blue`가 `backend-green`으로 바뀌는 점만 다르므로 한쪽을 이해한 뒤 중복 설명하지 않는다.

## 11.7 프론트 배포 Nginx의 API 분기

```nginx
location = /api {
    return 308 /api/;
}

location /api/ {
    proxy_pass http://${BACKEND_UPSTREAM}/;
}

location / {
    proxy_pass http://active_frontend;
}
```

`proxy_pass` 뒤에 `/`가 있어 `/api/posts`의 `/api/` prefix를 제거하고 백엔드에 `/posts`로 전달한다.

```text
/posts
→ active_frontend

/api/posts
→ BACKEND_UPSTREAM의 /posts
```

## 11.8 환경변수, 포트, 네트워크, 볼륨

```text
environment
→ 컨테이너 프로세스의 설정값

ports
→ host와 container 포트 연결

network
→ 컨테이너가 service 이름으로 통신

volume
→ 컨테이너 수명과 분리하여 데이터 보존

restart: unless-stopped
→ 사용자가 명시적으로 멈추지 않았다면 장애·재부팅 후 다시 실행
```

Compose의 `${VAR:?message}`는 값이 없으면 실행을 실패시키고, `${VAR:-default}`는 값이 없으면 기본값을 사용한다.

## 11.9 핵심 축약본

```text
백엔드 이미지
→ JDK로 bootJar
→ JRE로 실행

프론트 이미지
→ Node로 Vite build
→ Nginx로 dist 제공

Compose
→ blue, green, proxy, Redis 연결

Nginx
→ 활성 색상으로 요청 전달
→ 프론트 /api는 백엔드로 전달
```


## 11.9.1 전체 원문 코드 라인별 주석본

아래 주석은 학습을 위해 추가한 것이며 실제 프로젝트 파일에는 없다. 줄 끝 주석을 붙인 주석본은 의미를 따라가기 위한 설명용이므로 그대로 복사해 실행하지 않는다.

### 백엔드 Dockerfile

```dockerfile
# syntax=docker/dockerfile:1
# 위 syntax 지시문은 이 파일을 해석할 Dockerfile 문법 버전을 지정한다.
FROM eclipse-temurin:26-jdk AS build # Java 컴파일 도구가 포함된 JDK image를 build 단계로 시작한다.
WORKDIR /workspace # 이후 COPY와 RUN의 기본 작업 경로를 /workspace로 정한다.
COPY gradlew . # Gradle Wrapper 실행 파일을 먼저 복사한다.
COPY gradle gradle # Wrapper가 사용할 버전·JAR 디렉터리를 복사한다.
COPY build.gradle settings.gradle ./ # dependency 정의 파일을 소스보다 먼저 복사해 Docker cache를 활용한다.
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon # Wrapper 실행 권한을 주고 dependency layer를 미리 받는다.
COPY src src # dependency layer 뒤에 변경이 잦은 실제 소스를 복사한다.
RUN ./gradlew clean bootJar --no-daemon # main 소스를 컴파일하고 실행 가능한 Spring Boot JAR를 만든다. 이 명령 자체는 test Task를 실행하지 않는다.

FROM eclipse-temurin:26-jre AS runtime # 컴파일 도구가 없는 JRE image로 최종 실행 단계를 시작한다.
WORKDIR /app # 운영 프로세스의 기본 경로를 /app으로 정한다.
RUN groupadd --system spring && useradd --system --gid spring spring # root 대신 사용할 시스템 group과 user를 만든다.
COPY --from=build --chown=spring:spring /workspace/build/libs/*.jar app.jar # build 결과 JAR만 소유권과 함께 최종 image로 복사한다.
USER spring:spring # 이후 ENTRYPOINT를 일반 spring 사용자 권한으로 실행한다.
EXPOSE 8080 # 이 container가 8080에서 수신한다는 메타데이터를 남긴다.
ENTRYPOINT ["java", "-jar", "app.jar"] # container 시작 시 Spring Boot JAR를 실행한다.
```

### 프론트 Dockerfile

```dockerfile
# syntax=docker/dockerfile:1
# 위 syntax 지시문은 이 파일을 해석할 Dockerfile 문법 버전을 지정한다.
FROM node:24-alpine AS build # Vite build에 필요한 Node image를 build 단계로 시작한다.
WORKDIR /workspace # npm과 build가 실행될 경로를 지정한다.
COPY package.json package-lock.json ./ # dependency 목록과 잠금 파일을 소스보다 먼저 복사한다.
RUN npm ci # lock 파일과 정확히 일치하는 dependency를 깨끗하게 설치한다.
COPY . . # 나머지 프론트 소스와 설정을 복사한다.
ARG VITE_API_BASE_URL=/api # image build 호출자가 바꿀 수 있는 API 기준 주소 인자를 선언한다.
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} # Vite process가 읽도록 build 인자를 환경변수로 노출한다.
RUN npm run build # React 소스를 브라우저용 정적 dist로 묶는다.

FROM nginx:1.28-alpine AS runtime # 정적 파일 제공용 Nginx를 최종 단계로 사용한다.
COPY nginx/default.conf /etc/nginx/conf.d/default.conf # SPA와 asset 규칙이 있는 Nginx 설정을 복사한다.
COPY --from=build /workspace/dist /usr/share/nginx/html # build 결과 정적 파일만 Nginx document root로 복사한다.
EXPOSE 80 # Nginx가 container 80 포트에서 수신함을 표시한다.
CMD ["nginx", "-g", "daemon off;"] # Nginx를 foreground로 실행해 container 주 프로세스로 유지한다.
```

### 프론트 container Nginx

```nginx
server { # 하나의 HTTP 가상 서버 설정을 시작한다.
    listen 80; # container의 80 포트에서 요청을 받는다.
    server_name _; # 특정 도메인과 무관하게 기본 서버로 동작한다.
    root /usr/share/nginx/html; # 정적 파일을 찾을 기준 디렉터리다.
    index index.html; # 디렉터리 요청의 기본 문서다.
    client_max_body_size 20m; # 이미지 요청 등을 고려해 body 최대 크기를 20MB로 제한한다.

    location = /health { # 정확히 /health 요청만 처리한다.
        access_log off; # 반복 health check를 일반 access log에 남기지 않는다.
        default_type text/plain; # 응답 Content-Type을 text/plain으로 만든다.
        return 200 "ok\n"; # 다른 upstream 없이 즉시 정상 응답한다.
    }

    location /assets/ { # Vite가 만든 asset 경로 요청을 처리한다.
        try_files $uri =404; # 실제 파일이 있을 때만 제공하고 없으면 404를 반환한다.
        add_header Cache-Control "public, max-age=31536000, immutable"; # 해시 asset을 브라우저가 1년 cache하게 한다.
    }

    location / { # health와 assets 이외의 모든 화면 URL을 처리한다.
        try_files $uri $uri/ /index.html; # 실제 파일이 없으면 React Router 진입점 index.html을 반환한다.
    }
}
```

### Compose 핵심

```yaml
backend-blue:                         # blue 색상 Spring container 서비스다.
  image: ${BACKEND_IMAGE}:${BACKEND_BLUE_TAG} # Registry image 이름과 blue tag를 .env에서 조합한다.
  environment:                       # Spring 프로세스에 전달할 환경변수다.
    SPRING_PROFILES_ACTIVE: prod      # 운영 profile을 활성화한다.
    DB_URL: ${DB_URL}                 # RDS 연결 주소를 전달한다.
    REDIS_HOST: redis                 # 같은 network의 redis 서비스 이름을 host로 사용한다.
  ports:
    - "127.0.0.1:${BACKEND_BLUE_PORT:-8081}:8080" # 변수가 있으면 그 host 포트를, 없으면 8081을 container 8080에 연결한다.
  networks:
    - backend-network                # Redis와 Nginx가 함께 속한 network에 참여한다.
```

### 프론트 배포 Nginx 분기

```nginx
location = /api { # 슬래시 없는 정확한 /api 요청을 처리한다.
    return 308 /api/; # method를 보존하는 308로 /api/ 형태로 통일한다.
}

location /api/ { # /api/ 아래의 모든 API 요청을 처리한다.
    proxy_pass http://${BACKEND_UPSTREAM}/; # /api/ prefix를 제거하고 외부 백엔드 upstream에 전달한다.
}

location / { # API가 아닌 화면 요청을 처리한다.
    proxy_pass http://active_frontend; # 현재 blue 또는 green 프론트 container에 전달한다.
}
```


### Compose의 나머지 서비스 라인별 주석본

```yaml
services:                              # Compose가 함께 관리할 container 목록이다.
  redis:                               # backend가 조회수 저장소로 사용할 Redis 서비스 이름이다.
    image: redis:7.4-alpine            # Redis 7.4 Alpine image로 container를 만든다.
    volumes:
      - redis-data:/data               # Redis 저장 디렉터리를 이름 있는 volume과 연결한다.
    networks:
      - backend-network                # backend와 같은 내부 network에 참여한다.

  backend-green:                       # blue와 교대로 새 버전을 받을 Spring 서비스다.
    image: ${BACKEND_IMAGE}:${BACKEND_GREEN_TAG} # green 전용 tag의 backend image를 실행한다.
    ports:
      - "127.0.0.1:${BACKEND_GREEN_PORT:-8082}:8080" # 변수가 있으면 그 host 포트를, 없으면 8082를 green container 8080에 연결한다.
    networks:
      - backend-network                # Redis와 proxy가 접근할 같은 network에 참여한다.

  nginx:                               # 외부 요청을 현재 활성 backend에 전달할 proxy 서비스다.
    image: nginx:1.28-alpine           # Nginx 1.28 Alpine image를 사용한다.
    ports:
      - "${BACKEND_NGINX_PORT:-80}:80" # 변수가 있으면 그 host 포트를, 없으면 80을 Nginx container 80에 연결한다.
    networks:
      - backend-network                # blue와 green을 service 이름으로 찾을 network에 참여한다.

networks:
  backend-network:
    driver: bridge                     # 같은 host의 container를 연결하는 bridge network를 만든다.

volumes:
  redis-data:                          # Redis가 재생성돼도 유지할 이름 있는 volume을 선언한다.
```

### 백엔드 blue Nginx 라인별 주석본

```nginx
upstream active_backend { # proxy_pass가 참조할 활성 backend 그룹을 선언한다.
    server backend-blue:8080; # 현재 blue 설정에서는 Compose의 backend-blue 서비스로 전달한다.
}

server { # 외부 HTTP 요청을 받을 Nginx 가상 서버를 선언한다.
    listen 80; # container 80 포트에서 요청을 수신한다.
    server_name _; # 특정 domain과 일치하지 않는 요청도 이 기본 server가 받도록 한다.

    location = /health { # 정확히 /health인 배포 확인 요청을 처리한다.
        proxy_pass http://active_backend/actuator/health; # 활성 Spring의 Actuator health endpoint로 전달한다.
        proxy_set_header Host $host; # health 요청에도 원래 Host header를 전달한다.
        proxy_set_header X-Real-IP $remote_addr; # health 요청을 보낸 client의 IP를 전달한다.
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; # health 요청의 proxy 경로 IP 이력을 전달한다.
        proxy_set_header X-Forwarded-Proto $scheme; # health 요청의 원래 protocol을 전달한다.
    }

    location / { # health 이외의 모든 backend API 요청을 처리한다.
        proxy_pass http://active_backend; # 원래 URI를 유지하여 활성 backend로 전달한다.
        proxy_http_version 1.1; # upstream 통신에 HTTP/1.1을 사용한다.
        proxy_set_header Host $host; # 사용자가 요청한 host 정보를 backend에 전달한다.
        proxy_set_header X-Real-IP $remote_addr; # 실제 client IP를 별도 header에 전달한다.
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; # 여러 proxy를 거친 client IP 이력을 이어 붙인다.
        proxy_set_header X-Forwarded-Proto $scheme; # 원래 요청이 HTTP인지 HTTPS인지 backend에 알린다.
    }
}
```

green 파일에서는 `backend-blue` 한 줄만 `backend-green`으로 바뀌고 나머지 의미는 동일하다.

## 11.10 스킵할 코드

- blue와 green에 반복되는 동일 환경변수
- 동일한 proxy header 반복
- 프론트·백엔드 Compose의 동일 network 문법
- Dockerfile의 복사 경로를 제외한 들여쓰기 차이

다음은 스킵하지 않는다.

- build/runtime 멀티 스테이지
- 빌드 시 Vite 환경변수
- SPA `try_files`
- `/api/` proxy 경로 변환
- host/container 포트
- Redis volume과 AOF


## 11.10.1 이 장에서 필요한 Docker·Compose·Nginx 문법

### Dockerfile은 순서대로 layer를 만든다

각 `FROM`, `COPY`, `RUN`은 image layer 생성에 영향을 준다. 앞 단계 입력이 바뀌지 않으면 cache를 재사용할 수 있으므로 dependency 파일을 소스보다 먼저 복사한다.

### `FROM ... AS ...`

```dockerfile
FROM image AS build
```

기준 image로 새 build stage를 시작하고 이름을 붙인다. 뒤에서 `COPY --from=build`로 결과를 가져온다.

### `WORKDIR`

뒤의 상대 경로 `COPY`, `RUN`, `ENTRYPOINT`가 사용할 container 내부 기본 디렉터리를 만들고 선택한다. host의 현재 폴더를 바꾸는 명령이 아니다.

### `COPY`와 build context

`COPY`는 Docker build context 안의 파일만 image로 복사할 수 있다. `.dockerignore`는 context에서 제외할 파일을 정해 image 전송 크기와 불필요한 cache 무효화를 줄인다.

### `RUN`, `CMD`, `ENTRYPOINT`

- `RUN`: image를 만드는 동안 한 번 실행되고 결과가 layer에 저장
- `ENTRYPOINT`: container 시작 시 항상 실행할 주 프로그램
- `CMD`: 기본 실행 명령 또는 ENTRYPOINT의 기본 인자
- exec form `["java", "-jar", "app.jar"]`은 shell을 거치지 않고 프로세스를 직접 실행한다.

### Shell 연결 연산자

```dockerfile
RUN chmod +x gradlew && ./gradlew dependencies
```

`&&`는 앞 명령이 성공한 경우에만 뒤 명령을 실행한다. 한 RUN 안에서 실행되어 하나의 layer가 된다.

### `EXPOSE`

문서 성격의 image metadata다. 이것만으로 host port가 열리지는 않는다. 실제 연결은 `docker run -p`나 Compose `ports`가 만든다.

### Compose Map과 서비스 이름 DNS

Compose YAML의 `services` 아래 key가 서비스 이름이다. 같은 network의 container는 Docker DNS를 통해 `redis`, `backend-blue` 같은 서비스 이름을 host 이름처럼 사용할 수 있다.

### 환경변수 치환

```yaml
${NAME:?message}
${NAME:-default}
```

- `:?`: 값이 없으면 오류 메시지와 함께 Compose 중단
- `:-`: 값이 없거나 비어 있으면 기본값 사용
- 단순 `${NAME}`: 현재 환경이나 `.env` 값 치환

### ports 문법

```text
HOST_IP:HOST_PORT:CONTAINER_PORT
```

`127.0.0.1:8081:8080`은 EC2 내부 loopback 8081로 들어온 요청만 container 8080에 전달한다. `0.0.0.0` 또는 IP 생략은 외부 interface에도 공개될 수 있다.

### `expose`와 `ports`

- `expose`: container network 사용자에게 내부 포트를 문서화하며 host에 publish하지 않음
- `ports`: host와 container 포트를 실제 연결

### volume

이름 있는 volume은 container writable layer와 수명이 분리된다. container를 삭제·재생성해도 volume을 삭제하지 않으면 데이터가 남는다.

### healthcheck

Compose가 container process 존재 여부가 아니라 지정한 명령의 성공 여부로 건강 상태를 판단한다. `depends_on`만으로 애플리케이션 준비 완료가 자동 보장되는 것은 아니므로 health 조건이나 별도 대기 로직이 필요하다.

### Nginx directive와 block

```nginx
location /api/ {
    proxy_pass ...;
}
```

- directive는 세미콜론으로 끝난다.
- 중괄호 block은 하위 directive의 적용 범위를 만든다.
- `server`, `location`, `upstream`은 서로 다른 설정 문맥이다.

### `location` 일치

- `location = /health`: 정확히 같은 URI만
- `location /api/`: 해당 prefix로 시작하는 URI
- 더 구체적인 규칙이 일반 `/`보다 먼저 선택된다.

### `proxy_pass` 뒤 슬래시

```nginx
location /api/ {
    proxy_pass http://backend/;
}
```

upstream URL에 URI `/`가 있으면 일치한 `/api/` 부분을 `/`로 교체하여 `/api/posts`를 `/posts`로 전달한다. 뒤 슬래시가 없으면 URI 전달 결과가 달라질 수 있으므로 중요하다.

### Proxy header

Nginx를 거치면 backend가 직접 본 연결 상대는 Nginx다. `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`로 원래 client와 protocol 정보를 전달한다.

### `try_files`

인자를 왼쪽부터 실제 파일·디렉터리로 확인하고 모두 없으면 마지막 fallback을 사용한다. SPA에서는 `/index.html`을 반환해 React Router가 URL을 처리하게 한다.

### `envsubst`

환경변수 표시가 있는 template을 읽어 실제 값으로 치환한 Nginx 설정을 만든다. Nginx 자체가 모든 shell 환경변수를 설정 파일에서 자동 해석하는 것은 아니다.

## 11.11 이해 확인

1. Image와 Container는 무엇이 다른가?
2. 백엔드 Dockerfile에서 JDK와 JRE 단계를 분리한 이유는 무엇인가?
3. 최종 이미지에서 일반 사용자로 실행하는 이유는 무엇인가?
4. `VITE_API_BASE_URL`은 build 시점과 runtime 중 언제 번들에 들어가는가?
5. 프론트 Nginx가 `/posts` 요청에 `index.html`을 반환하는 이유는 무엇인가?
6. Compose network 안에서 backend가 `redis`라는 이름으로 연결할 수 있는 이유는 무엇인가?
7. host port와 container port는 어떻게 다른가?
8. `/api/posts`가 백엔드 `/posts`로 바뀌는 Nginx 설정은 무엇인가?
9. Docker volume은 Redis 데이터에 어떤 영향을 주는가?
10. blue와 green Nginx 파일을 모두 반복해서 깊게 읽지 않아도 되는 이유는 무엇인가?

## 11.12 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
