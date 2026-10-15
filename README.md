# Copilot Backend

This Spring Boot application will serve as a bridge between the frontend and the Python API.

It will hold the logic to authenticate users and to manage roles and permissions.

## Build

Requires JDK 25 and Maven. The application uses Spring Boot 4.1.1 and Spring AI 2.0.1.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -o clean verify
```

If dependencies are not cached, repeat without `-o` using the configured Maven repositories.

The optional integration check starts an isolated PostgreSQL container using the existing
test image configuration, applies Flyway migrations, and exercises HTTP and pgvector:

```powershell
mvn -o test -Dtest=MigrationStartupIT
```

It requires Docker and does not contact production services. LLM responses are tested with
a local HTTP stub; mail and JMS connectivity are not covered by this integration check.

## Catalog import / export (sources & workspaces)

Admins can create or update sources and workspaces in bulk from a YAML file:

- `POST /api/admin/catalog/import` (multipart, part `file`, max 1 MB) upserts by `name`.
  Elements missing from the file are left untouched; for existing elements the file wins
  (an omitted field becomes empty). The import is all-or-nothing: any error returns `400`
  with the list of `messages` and nothing is persisted.
- `GET /api/admin/catalog/export` downloads the current catalog in the same format.

```yaml
version: 1
sources:                     # processed before workspaces
  - name: avs                # required, unique key
    description: Assurance-vieillesse et survivants
    hypotheticalQuestions:
      - Quel est l'âge de référence ?
workspaces:
  - name: assurances-sociales
    description: Workspace généraliste
    hypotheticalQuestions: []
    sources: [avs]           # sources from this file or already in the database
```

Unknown keys are rejected. All scalars except `null` are read as strings, so `yes` or dates are kept verbatim.

## Spring AI 2 migration

Application JSON uses Jackson 3 (`tools.jackson`); Jackson annotations remain in
`com.fasterxml.jackson.annotation`. Jackson 2 may still be required by third-party libraries.

The OpenAI integration now uses the official Java SDK. Keep the existing internal model
base URLs unchanged: `ChatModelsConfig` appends `/v1` to preserve the previous request paths.
SDK transport and retry defaults replace the former Spring AI `RetryUtils` implementation.
ChatClient options are passed as builders so that model defaults are retained.

References: [Spring AI upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)
and [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).