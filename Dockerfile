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

# ---------- Stage 3: the small image that actually runs ----------
FROM eclipse-temurin:25-jre
# Run as an ordinary user, not root: if the app were ever compromised, the attacker would have
# no admin rights inside the container.
RUN groupadd --system fitcheck && useradd --system --gid fitcheck --uid 10001 --no-create-home fitcheck
WORKDIR /app
COPY --from=backend /build/app.jar app.jar
USER 10001

# Production settings by default; every secret/address comes from environment variables
# (see application-prod.yml). Nothing secret is baked into the image.
ENV SPRING_PROFILES_ACTIVE=prod
# Container-aware Java: the heap may use up to 60% of the container's memory limit (614 MiB of 1 GiB).
# The other ~40% is needed OUTSIDE the heap: loaded classes, threads, network and image buffers.
# (75% left too little room: phone-photo uploads pushed the total past 1 GiB and the container was
# killed. Found by the step 5.11 restart alert, reproduced with run-prod-local.ps1.)
# If the heap itself ever runs out, exit cleanly so Azure restarts the app instead of it limping along.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
