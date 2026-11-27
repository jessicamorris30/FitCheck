# FitCheck as ONE container: the Spring Boot backend, with the built React app inside it.
#
# A "multi-stage" build: each FROM starts a fresh stage. The first two stages have all the build
# tools (Node, the JDK, Maven); only their OUTPUT (one .jar) is copied into the small final stage.
# So the image that runs in Azure contains no compilers, no node_modules, no source code.
#
#   docker build -t fitcheck .
#   docker run -p 8080:8080 -e ... fitcheck        (see scripts/run-prod-local.ps1)

# ---------- Stage 1: build the React frontend ----------
FROM node:24-alpine AS frontend
WORKDIR /build/frontend
# Copy only the dependency list first: Docker caches each step, so as long as package*.json don't
# change, "npm ci" is skipped on the next build even when the source code changed.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
# → /build/frontend/dist (index.html + assets/)

# ---------- Stage 2: build the Spring Boot backend (with the frontend inside) ----------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build/backend
# Same caching trick: download all Maven dependencies in their own step.
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
# The built frontend becomes the backend's static files (served by WebConfig, step 5.6).
COPY --from=frontend /build/frontend/dist src/main/resources/static
# Tests already ran in CI (and need Postgres/Azurite), so they're skipped here.
RUN ./mvnw -B -q package -DskipTests && cp target/fitcheck-backend-*.jar /build/app.jar
# "Extract" the jar into app.jar + lib/ (Spring Boot's recommended layout for the startup cache in
# stage 3: the cache needs plain jar files on the class path, not jars nested inside one big jar).
# Done here, not in stage 3, so the final image only ever contains the extracted copy.
RUN java -Djarmode=tools -jar /build/app.jar extract --destination /build/extracted

# ---------- Stage 3: the small image that actually runs ----------
FROM eclipse-temurin:25-jre
# Run as an ordinary user, not root: if the app were ever compromised, the attacker would have
# no admin rights inside the container.
RUN groupadd --system fitcheck && useradd --system --gid fitcheck --uid 10001 --no-create-home fitcheck
WORKDIR /app
# The libraries change rarely, the app often: two COPYs let Docker reuse the libraries layer.
COPY --from=backend /build/extracted/lib lib
COPY --from=backend /build/extracted/app.jar app.jar

# Production settings by default; every secret/address comes from environment variables
# (see application-prod.yml). Nothing secret is baked into the image.
ENV SPRING_PROFILES_ACTIVE=prod
# Container-aware Java: the heap may use up to 60% of the container's memory limit (614 MiB of 1 GiB).
# The other ~40% is needed OUTSIDE the heap: loaded classes, threads, network and image buffers.
# (75% left too little room: phone-photo uploads pushed the total past 1 GiB and the container was
# killed. Found by the step 5.11 restart alert, reproduced with run-prod-local.ps1.)
# If the heap itself ever runs out, exit cleanly so Azure restarts the app instead of it limping along.
# SerialGC: what Java picks by itself on half a CPU anyway; stated explicitly so the startup cache
# (recorded on the build machine, which has more CPUs) is made with the same settings it's used with.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError -XX:+UseSerialGC"

# Startup cache (Java 25 "AOT cache"): start the app once here, during the build, and record the
# thousands of classes it loads and prepares. Real starts then map that recording in instead of
# redoing the work, which makes the app start much faster after a scale-to-zero.
# spring.context.exit=onRefresh stops the practice run once the app is set up, and the -D settings
# keep it from looking for a database (no migration check, no schema check), so it needs no
# database or secrets. Those are app settings, not Java ones, so the cache still fits real starts,
# which do run the migrations and checks as usual. The obviously fake values below only satisfy the "this setting is
# required" checks in application-prod.yml; nothing connects to them, and they exist for this one
# command only (they are not stored in the image).
RUN DB_URL=jdbc:postgresql://build.invalid:5432/none POSTGRES_USER=none POSTGRES_PASSWORD=none \
    MAIL_HOST=build.invalid MAIL_USERNAME=none MAIL_PASSWORD=none MAIL_FROM=none@build.invalid \
    CORS_ALLOWED_ORIGINS=https://build.invalid FRONTEND_URL=https://build.invalid \
    JWT_SECRET=bm90LWEtc2VjcmV0LWp1c3QtYS1idWlsZC10aW1lLXBsYWNlaG9sZGVy \
    AZURE_STORAGE_CONNECTION_STRING="DefaultEndpointsProtocol=https;AccountName=none;AccountKey=bm9uZQ==;EndpointSuffix=build.invalid" \
    java -XX:AOTCacheOutput=/app/app.aot -Dspring.context.exit=onRefresh \
         -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
         -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
         -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
         -jar /app/app.jar

USER 10001
EXPOSE 8080
# If the cache ever doesn't match (e.g. a different Java), Java just ignores it and starts normally.
ENTRYPOINT ["java", "-XX:AOTCache=/app/app.aot", "-jar", "/app/app.jar"]
