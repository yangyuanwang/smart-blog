# 后端镜像：多阶段构建，分层 jar 利用缓存
# 构建上下文 = backend/，docker compose -f docker/docker-compose.yml --profile full build backend

# 构建阶段：Maven + JDK 21（settings.xml 走阿里云镜像加速依赖下载）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY docker-settings.xml /root/.m2/settings.xml
COPY pom.xml .
# 先拉依赖，源码变更时利用层缓存
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests && java -Djarmode=layertools -jar target/smart-blog-2.0.0.jar extract --destination extracted

# 运行阶段：JRE 21
FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
