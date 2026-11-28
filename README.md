# FitCheck

[![CI](https://github.com/jessicamorris30/FitCheck/actions/workflows/ci.yml/badge.svg)](https://github.com/jessicamorris30/FitCheck/actions/workflows/ci.yml)

FitCheck is a web app for digitizing your wardrobe and planning outfits.

- **Sign up** with email and password.
- **Upload photos** of your clothes to build a digital wardrobe.
- **Browse** your wardrobe by category.
- **Plan mode**: put outfits together yourself, or ask AI (Claude) to suggest outfits from your own clothes based on a text or image prompt describing the aesthetic you want.
- **Calendar**: put saved outfits on days ("wear this on Friday"), several per day, in a week or month view; past days show what you wore.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Tailwind CSS v4, Vite, React Router, axios |
| Backend | Java 25, Spring Boot 4 (Web MVC, Security, Data JPA, Validation), Maven |
| Database | PostgreSQL 16, schema managed by Flyway |
| Image storage | Azure Blob Storage (Azurite emulator locally) |
| AI | Claude API via the official `anthropic-java` SDK |
| Auth | Short-lived JWT access tokens + rotating refresh tokens in HttpOnly cookies |

## Repository layout

```
FitCheck/
├── backend/            Spring Boot API (com.fitcheck.*)
├── frontend/           React + TypeScript app
├── docs/               Architecture, API, database, auth, AI and setup docs
├── docker-compose.yml  Local Postgres + Azurite
└── .env.example        Environment variables (copy to .env)
```

## Quick start

Prerequisites: JDK 25, Node.js 20+, and Docker Desktop. Full details in [docs/SETUP.md](docs/SETUP.md).

```bash
cp .env.example .env            # then fill in JWT_SECRET (and ANTHROPIC_API_KEY for AI features)
docker compose up -d            # Postgres on :5432, Azurite on :10000

cd backend && ./mvnw spring-boot:run      # API on http://localhost:8080 (Windows: .\mvnw.cmd spring-boot:run)
cd frontend && npm install && npm run dev # UI on http://localhost:5173
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [REST API](docs/API.md)
- [Database](docs/DATABASE.md)
- [Authentication](docs/AUTH.md)
- [AI integration](docs/AI_INTEGRATION.md)
- [Local setup](docs/SETUP.md)
- [Roadmap](docs/ROADMAP.md)

## Status

Project skeleton only. Most backend classes and frontend components are placeholders with TODOs; see the [roadmap](docs/ROADMAP.md).
