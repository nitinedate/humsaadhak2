# One image: React app built into the Spring Boot JAR. Java is Amazon Corretto 17 (AWS's OpenJDK) to build and to run.
# npm and Maven downloads stay in build caches (not part of the image): a rerun after a network error resumes them, and
# both retry slow or failed downloads instead of giving up.
FROM node:22-bookworm-slim AS web
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --fetch-retries=5 --fetch-retry-mintimeout=20000 --fetch-retry-maxtimeout=120000
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-amazoncorretto-17 AS jar
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -Daether.connector.http.retryHandler.count=5 -Daether.connector.requestTimeout=120000 -f backend/pom.xml dependency:go-offline
COPY backend backend
COPY --from=web /src/frontend/dist frontend/dist
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -Daether.connector.http.retryHandler.count=5 -Daether.connector.requestTimeout=120000 -f backend/pom.xml -DskipTests package

# Amazon Corretto 17 on Amazon Linux 2023, headless (no desktop libraries): the server runtime.
FROM amazoncorretto:17-al2023-headless
# A fixed numeric user, 10001, owns its TLS key (deploy/compose.yaml); no package install is needed for it.
RUN mkdir -p /app && chown 10001:10001 /app
# Java 17 takes its default charset from the locale: pin UTF-8 (₹, names in Indian scripts) whatever the host sets.
ENV LANG=C.UTF-8 HOME=/app
USER 10001:10001
WORKDIR /app
COPY --from=jar --chown=10001:10001 /src/backend/target/humsaadhak-app-*.jar app.jar
EXPOSE 8443
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-Duser.home=/app", "-jar", "app.jar"]
