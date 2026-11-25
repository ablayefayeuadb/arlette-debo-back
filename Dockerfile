# --- Build ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination /extract

# --- Runtime : JRE minimal, utilisateur non root ----------------------------
FROM eclipse-temurin:21-jre-alpine
# UID/GID fixes (10001) : le dossier des justificatifs du serveur leur appartient
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app && mkdir -p /data/storage && chown app:app /data/storage
WORKDIR /app
COPY --from=build /extract/dependencies/ ./
COPY --from=build /extract/spring-boot-loader/ ./
COPY --from=build /extract/snapshot-dependencies/ ./
COPY --from=build /extract/application/ ./
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_ROOT=/data/storage \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
