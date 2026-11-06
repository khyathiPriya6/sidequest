# SideQuest API

Spring Boot REST API using Java 21, PostgreSQL, and Liquibase.

## Run locally

From the repository root, prepare a local configuration file:

```sh
cd backend
cp .env.example .env.properties
```

Edit `.env.properties` locally and fill in `DATABASE_PASSWORD`. This file is ignored by Git and is automatically loaded when you start Maven from `backend/`. Use Java properties syntax: no surrounding quotes, and double literal backslashes. Environment variables override values in this file.

Create the separate `sidequest` database first using your existing PostgreSQL administrator connection (for example, execute `CREATE DATABASE sidequest;` in DataGrip with auto-commit enabled). Configuring a database name does not create it. Do not point this app at another project's database.

Then run from `backend/`:

```sh
mvn spring-boot:run
```

The API listens on port `8080` by default. The PostgreSQL defaults are host `localhost`, port `5432`, database `sidequest`, and username `postgres`. Port `5432` was confirmed to accept connections. Override them with `DATABASE_URL`, or use `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` (omit `DATABASE_URL` to use the port/name values). `SERVER_PORT` changes the API port. Plain `.env` files are not loaded; use `.env.properties` as described above.

The database must already exist and accept the configured user. No credentials are committed. Liquibase is enabled; its initial changelog is intentionally a no-op until an issue defines domain tables.

Health endpoint: `GET http://localhost:8080/api/v1/health`

Run the controller contract test with `mvn test`. For a local integration check without applying Liquibase migrations, use:

```sh
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.liquibase.enabled=false
```

This still connects to PostgreSQL. The health response reports API availability; it is not a continuous database health probe. The project targets Java 21; the current local checks used installed JDK 26.0.1 with compiler release 21.
