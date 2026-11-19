# Local development setup

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 25 | `java -version` |
| Node.js | 20 or newer | `node -v` |
| Docker Desktop | with Compose v2 | `docker compose version` |

You don't need to install Maven; the backend includes the Maven Wrapper (`mvnw` / `mvnw.cmd`).

Set `JAVA_HOME` to your JDK folder (for example `C:\Program Files\Eclipse Adoptium\jdk-25.x.x-hotspot`) so the Maven Wrapper and IDEs find it. Restart your terminal and editor after changing environment variables.

Recommended VS Code extensions are listed in `.vscode/extensions.json`. VS Code offers to install them when you open the folder.

## 1. Environment variables

```bash
cp .env.example .env
```

Fill in:

- `JWT_SECRET`: a base64 string of at least 32 random bytes. The command to generate one is in `.env.example`.
- `ANTHROPIC_API_KEY`: needed only for AI outfit suggestions.

The defaults for the database and Azurite already match `docker-compose.yml`.

Both Docker Compose and the backend read the repo-root `.env` file. The backend imports `../.env` through `spring.config.import`, so run it from the `backend/` folder.

## 2. Start Postgres and Azurite

```bash
docker compose up -d
docker compose ps        # both services should be running; postgres should be "healthy"
```

| Service | Port | Purpose |
|---|---|---|
| postgres | 5432 | Database `fitcheck` (user and password `fitcheck` by default) |
| azurite | 10000 | Azure Blob Storage emulator |

To wipe local data, run `docker compose down -v`.

## 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run          # Windows PowerShell: .\mvnw.cmd spring-boot:run
```

- The API listens on http://localhost:8080. To check it's running, open http://localhost:8080/actuator/health.
- On startup, Flyway applies the migrations in `src/main/resources/db/migration`.
- The `dev` profile is active by default (`application-dev.yml`).
- `./mvnw test` runs the tests. `FitCheckApplicationTests` starts the full application, so Postgres must be running.

## 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

- The UI runs at http://localhost:5173.
- The Vite dev server forwards `/api/*` to `http://localhost:8080`, so the browser treats the API as the same site and the refresh-token cookie works.

## Troubleshooting

- **Port 5432 already in use**: another Postgres instance is running. Stop it, or change the port mapping in `docker-compose.yml` and `DB_URL`.
- **`JAVA_HOME` not found**: set it as described above and open a new terminal.
- **Flyway checksum mismatch**: a migration that has already run was edited. Never edit an applied migration; add a new `V<n>__*.sql`. For local data you don't need, `docker compose down -v` resets the database.
