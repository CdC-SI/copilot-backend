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
- **ChatClient** : injecter le builder qualifié (`@Qualifier(ChatClientConfig.INTERNAL_CHAT_CLIENT_BUILDER)` ou
  `VISION_CHAT_CLIENT_BUILDER`) et appeler `build()` ; jamais `ChatClient.create(...)` ni d'appel direct au `ChatModel`
  (observations et advisors communs centralisés dans `ChatClientConfig`). En test : `ChatClientConfig.builder(modèle, ObservationRegistry.NOOP)`.
- **Résilience LLM** : le modèle interne est un `ResilientChatModel` (timeouts, retry avant le premier token, circuit
  breaker, modèle de repli — `zia.llm.resilience.*`). Ne pas ajouter de retry ailleurs ; le client SDK a `maxRetries(0)`.
- **Observabilité** (package `observability`) : modèles construits avec l'`ObservationRegistry` (spans `gen_ai`, outils,
  `gen_ai.client.token.usage`) ; export OTLP désactivé par défaut (`OTEL_TRACES_EXPORT_ENABLED`). `X-Correlation-Id` :
  lu/généré par jweb (MDC `correlation`), propagé entre threads (context-propagation Reactor `auto`) et sur les appels
  sortants — tout nouveau `WebClient` interne applique `CorrelationId.webClientFilter()` ; les `RestClient` issus du
  builder Boot l'ont déjà. Usage LLM d'un tour → métriques `zia.llm.tokens` / `zia.llm.cost` (`TurnUsageEvent`).
- Respecter le style réactif existant (`Flux<Token>`, SSE) dans les flux de conversation.
- Prompts LLM dans `src/main/resources/prompts/<fr|de|it>/<clé>.txt`, déclarés dans `PromptId` et servis par `PromptRegistry` (validation au démarrage : fichier présent dans les 3 langues, variables conformes ; repli sur `de`). Variables `{{nom}}` rendues par `Prompt.render` (une passe, variables exactes) ; seuls `query-rewriter`/`query-expander` gardent la syntaxe Spring AI `{nom}` (`Prompt.template()`). La version (SHA-256 court) des prompts utilisés est tracée dans `consulted_sources.prompts` (`TurnTrace.prompts`, tools via `ToolContextKeys.recordPrompt`).
- Recherche documentaire (T2) : `rag.retrieval.provider` choisit le moteur de `RAGTool` : `local` (défaut : pgvector, BM25, `DocumentReranker`, `RankedDocumentJoiner`) ou `zia-rag` (`ZiaRagDocumentRetriever` → `ZiaRagClient`, `ZIA_RAG_BASE_URL`). Le jeton Blue de l'utilisateur va du contrôleur au tool par le contexte Reactor (`UserToken`) puis `ToolContextKeys.CTX_USER_TOKEN`. Le chemin zia-rag ne filtre pas sur `organizations`. Les retrievers locaux et `get_documents` (`VectorStoreLookup`) restent locaux, à retirer après la bascule.
- Mémoire conversationnelle : `ConversationMemory` fenêtre l'historique envoyé au LLM par tours (budget `zia.memory.history-token-budget`, dernier tour toujours conservé) et préfixe le résumé persistant (`conversation_summary`, prompt `conversation-summary`) au premier message gardé. La compaction est asynchrone après `saveExchange` (une à la fois par conversation, jusqu'à `compaction-ratio` du budget, jamais le dernier tour) ; `zia.memory.summary-enabled=false` désactive le résumé. L'historique complet reste utilisé pour la numérotation des `<retrieved_sources>`.
- **Tools** : toujours passer les callbacks par `ToolCallRecorder.wrap(...)` (journal persisté, événements SSE
  `tool_call`/`tool_result`, approbation `approval_required` des tools listés dans `zia.tools.approval.required-tools`).

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
