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
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The API listens on port `8080` by default. The PostgreSQL defaults are host `localhost`, port `5432`, database `sidequest`, and username `postgres`. Port `5432` was confirmed to accept connections. Override them with `DATABASE_URL`, or use `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` (omit `DATABASE_URL` to use the port/name values). `SERVER_PORT` changes the API port. Plain `.env` files are not loaded; use `.env.properties` as described above.

The database must already exist and accept the configured user. No credentials are committed. **Obtain owner approval before starting against an existing database:** Liquibase now creates users and JDBC session tables. Review the target and back it up first. The local profile uses an HTTP development cookie; deployments must use HTTPS and must never activate `local`.

Health endpoint: `GET http://localhost:8080/api/v1/health`

Use Java 21 for builds and runtime. Run unit tests with `mvn test`; run persistence/security/API tests with `bash scripts/test-postgres.sh` after setting JAVA_HOME to Java 21. The runner creates and stops a fresh temporary PostgreSQL cluster and never uses developer connection settings. Disabling Liquibase is not a substitute for schema preparation: Hibernate validates the required tables.

The health response reports API availability; it is not a continuous database health probe. See [authentication usage and tests](../docs/implementation/AUTHENTICATION-API.md), [OpenAPI](../docs/api/auth.openapi.yaml), [migration/rollback notes](../docs/implementation/IDENTITY-PERSISTENCE.md), and [implementation tracker](../docs/planning/IMPLEMENTATION-TRACKER.md). Frontend integration remains gated by PR #37 and the registration design handoff.
