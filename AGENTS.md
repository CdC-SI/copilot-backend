# AGENTS.md — copilot-backend

Backend Spring Boot de ZIA (ZAS/EAK-Copilot) : assistant RAG pour les assurances sociales suisses,
basé sur Spring AI, avec une boucle agentique (le LLM appelle des tools `@Tool`).

## Stack

- **Java 25** (records, sealed interfaces, pattern matching, `var`, `List.copyOf`…).
- **Spring Boot 4.1.x**, **Spring AI 2.0.x** (`spring-ai-openai`, `spring-ai-rag`, `spring-ai-client-chat`, pgvector).
- **PostgreSQL** + **pgvector**, migrations **Flyway** (`src/main/resources/db/migration`).
- **Maven**, **Lombok**, **MapStruct**, **Reactor** (`Flux`/`Mono`, SSE).

## Build & tests

- Utiliser `mvn` (pas `./mvnw` / `mvnw.cmd`). Privilégier le mode hors-ligne `-o` quand possible.
- Compiler : `mvn -o compile`
- Tests : `mvn -o test` ; un seul test : `mvn -o test -Dtest=NomDeLaClasseTest`
- Build complet (référence, utilisé par le `Jenkinsfile`) : `mvn clean verify`
- Tests d'intégration `*IT` (Testcontainers, **Docker requis**) : ex. `mvn -o test -Dtest=MigrationStartupIT`.
  `ChatModelsConfigTest` (`src/test/java/zas/admin/zec/backend/config/ChatModelsConfigTest.java`) contient un stub HTTP du LLM réutilisable.
- Après une modification, valider au minimum avec `mvn -o compile` avant de conclure.

## Organisation du code (`zas.admin.zec.backend`)

- `actions/<cas d'usage>/` : controllers REST + services applicatifs (`converse`, `upload`, `authorize`,
  `api`, `summarize`, `workspace`, `alert`, `rate`…).
- `tools/` : tools Spring AI (`@Tool`) et services utilitaires (ex. `RAGTool`, `TariffService`, `document/`).
- `rag/` : pipeline RAG (`retriever`, `reranker`, `joiner`, `token`, `validation`, prompts).
- `config/` (dont `security/`, `properties/`), `persistence/` (`entity/`, `repository/`).

## Conventions de code

- **Tools Spring AI** : méthodes annotées `@Tool` (nom + description claire pour guider le LLM) et
  `@ToolParam` pour les arguments fournis par le LLM. Les données **non fournies par le LLM** (userId,
  langue, workspace, conversationId, droits) transitent par le `ToolContext` ; clés en constantes réutilisables.
- Injection par constructeur (pas de `@Autowired` sur les champs).
- **Lombok** conformément à l'existant (`@Slf4j` pour le logging).
- DTO/payloads en **records** (souvent `@Builder` + validation `jakarta.validation`).
- Respecter le style réactif existant (`Flux<Token>`, SSE) dans les flux de conversation.
- Prompts LLM centralisés (ex. `RAGPrompts`) et **multilingues** (fr/de/it) : conserver cette structure.

## Bonnes pratiques

- Ne pas casser les chemins existants (sécurité par clé d'API de `/api/public/v1/**`, API publique à reconstruire en V5) ni `ConversationService` lors de
  refactorings progressifs.
- Lors d'un remplacement par étapes, marquer l'ancien code `@Deprecated(forRemoval = true)` avec un
  commentaire expliquant la suite, plutôt que de tout supprimer d'un coup.
- Ne pas committer de secrets ni de keystores (`*.p12`) ; les profils de config sont dans
  `src/main/resources/application*.yml`.

## Git

- Base d'intégration : `Jarvis` ; production : `master`. Jamais de commit direct sur ces branches.
- Branches de travail : `feature/<nom>` (ou `chore/…`, `bugfix/…`) depuis `Jarvis`, PR vers `Jarvis`.
- Messages de commit au format Conventional Commits (`feat: …`, `fix: …`, `chore: …`).
