# Powered by Doctor(TM)

# 前端构建
FROM node:20 AS frontend-build
WORKDIR /app/web
COPY web/package.json web/pnpm-lock.yaml ./
RUN npm install
COPY web .
RUN npm run build

# 安装 Python，生成资源包
RUN apt-get update && apt-get install -y python3 && rm -rf /var/lib/apt/lists/*
RUN python3 static-bundle.py

# 后端构建
FROM gradle:8.14-jdk21-ubi AS backend-build
WORKDIR /app
COPY . .
RUN chown -R gradle:gradle /app /home/gradle
USER gradle
RUN gradle build --no-daemon -x test

# 最终镜像
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY --from=backend-build /app/build/libs/*.jar app.jar
COPY --from=frontend-build /app/web/dist/ ./static/
COPY src/main/resources/application-dev.yml ./application-dev.yml
COPY scripts/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

RUN apt-get update && apt-get install -y gettext-base && rm -rf /var/lib/apt/lists/*

# 环境变量（可在 docker-compose 里覆盖）
ENV SPRING_DATASOURCE_URL="jdbc:postgresql://db:5432/station"
ENV SPRING_DATASOURCE_USERNAME="station"
ENV SPRING_DATASOURCE_PASSWORD="station_password"
ENV OAUTH2_CLIENT_ID=""
ENV OAUTH2_CLIENT_SECRET=""
ENV SWAGGER_UI_ENABLED="true"
ENV S3_ENDPOINT=""
ENV S3_REGION="auto"
ENV S3_BUCKET=""
ENV S3_PUBLIC_BASE_URL=""
ENV S3_PATH_STYLE_ACCESS="false"
ENV S3_ACCESS_KEY=""
ENV S3_SECRET_KEY=""
ENV S3_SESSION_TOKEN=""
ENV S3_WORLDMAP_PREFIX="station"
ENV S3_PICBED_PREFIX="picbed"

EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
