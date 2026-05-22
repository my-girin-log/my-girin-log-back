# --- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 캐시 최적화: gradle 파일만 먼저 복사
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# wrapper 가 깨지지 않게 + 실행권한
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 + 빌드 (테스트 제외 — CI 가 따로 검증)
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# --- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Render free tier 는 RAM 512MB. JVM heap 을 RAM 의 70% 까지만 사용.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=render

# 빌드된 jar 1개만 복사
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
