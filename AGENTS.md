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
- Build complet (référence, utilisé par le `Jenkinsfile`) : `mvn clean verify`. Il exécute les tests unitaires (surefire),
  puis les tests d'intégration `*IT` (failsafe, **Docker requis**, un échec casse le build), puis le rapport de couverture
  JaCoCo (`target/site/jacoco/index.html`) et ses seuils (`jacoco.minimum.*` dans le `pom.xml`). Ces seuils sont à cliquet :
  on les relève au niveau atteint, on ne les baisse jamais.
- Tests d'intégration `*IT` (Testcontainers) seuls : ex. `mvn -o test -Dtest=MigrationStartupIT`.
  `ChatModelsConfigTest` (`src/test/java/zas/admin/zec/backend/config/ChatModelsConfigTest.java`) contient un stub HTTP du LLM réutilisable.
- Mockito est chargé comme agent Java (`argLine` de surefire et failsafe). Un `argLine` ajouté doit garder `@{argLine}`,
  qui porte l'agent JaCoCo.
- Tout nouveau code s'accompagne de ses tests ; une baisse de couverture fait échouer `verify`.
- Après une modification, valider au minimum avec `mvn -o compile` avant de conclure.

## Organisation du code (`zas.admin.zec.backend`)

- `actions/<cas d'usage>/` : controllers REST + services applicatifs (`converse`, `upload`, `authorize`,
  `api`, `summarize`, `workspace`, `alert`, `rate`…).
- `tools/` : tools Spring AI (`@Tool`) et services utilitaires (ex. `RAGTool`, `TariffService`, `document/`).
- `rag/` : pipeline RAG (`remote` : client zia-rag, `token`, `validation`, traces et usage des tours).
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
- Prompts LLM dans `src/main/resources/prompts/<fr|de|it>/<clé>.txt`, déclarés dans `PromptId` et servis par `PromptRegistry` (validation au démarrage : fichier présent dans les 3 langues, variables conformes ; repli sur `de`). Variables `{{nom}}` rendues par `Prompt.render` (une passe, variables exactes) La version (SHA-256 court) des prompts utilisés est tracée dans `consulted_sources.prompts` (`TurnTrace.prompts`, tools via `ToolContextKeys.recordPrompt`).
- Recherche documentaire : `RAGTool` délègue tout à zia-rag (`ZiaRagDocumentRetriever` → `ZiaRagClient`, `rag.zia-rag.*`, `ZIA_RAG_BASE_URL` obligatoire) : une requête par appel (celle du LLM), filtre sur les sources du workspace et `userId` ; nombre de documents, mode (vectoriel, BM25, hybride), reranking et seuils sont les valeurs par défaut de zia-rag (`ZIA_RAG_DEFAULT_*`, `ZIA_RAG_MIN_RERANK_SCORE`). Pas de réécriture, d'extension de requête ni de reranking dans le backend : les ajouter dans zia-rag. L'inférence du workspace reste dans `RAGTool`. Le jeton Blue de l'utilisateur va du contrôleur au tool par le contexte Reactor (`UserToken`) puis `ToolContextKeys.CTX_USER_TOKEN`. `get_documents` (`VectorStoreLookup`, recherche par métadonnées) lit toujours `vector_store` en local. La métadonnée `organizations` des documents n'est plus filtrée nulle part (tous les utilisateurs sont internes : RAG, `get_documents`, processus métier, sources et tags des paramètres) ; seuls les documents personnels (`user_uuid`) des autres utilisateurs sont exclus. La distinction interne/externe reste au niveau utilisateur (`internalUser`, `@RequireInternalUser`), à retirer plus tard.
- Mémoire conversationnelle : `ConversationMemory` fenêtre l'historique envoyé au LLM par tours (budget `zia.memory.history-token-budget`, dernier tour toujours conservé) et préfixe le résumé persistant (`conversation_summary`, prompt `conversation-summary`) au premier message gardé. La compaction est asynchrone après `saveExchange` (une à la fois par conversation, jusqu'à `compaction-ratio` du budget, jamais le dernier tour) ; `zia.memory.summary-enabled=false` désactive le résumé. L'historique complet reste utilisé pour la numérotation des `<retrieved_sources>`.
- **Agent unique** (F17) : `RAGChatService` traite toutes les conversations (`ConversationType.COMPLETE`, plus de mode
  « sans Corpus »). Le prompt `agentic-system` impose la recherche documentaire pour le domaine officiel (1er pilier, RH,
  CdC) et laisse le LLM répondre directement aux demandes générales ; `Question.ragEnabled` est ignoré (à retirer).
- **Tools** : toujours passer les callbacks par `ToolCallRecorder.wrap(...)` (journal persisté, événements SSE
  `tool_call`/`tool_result`, approbation `approval_required` des tools listés dans `zia.tools.approval.required-tools`).
- **Citations** (F2a) : tout extrait lu avec son contenu reçoit une référence courte par tour (`ref` de `<document>` /
  `<attachment>`, via `ToolContextKeys.citationRef` et `CitationRefs`) ; l'agent écrit `[ref]`, `CitationStreamFilter`
  le remplace par `[^n]` (numéro affiché, ordre de première citation, partagé par les extraits d'une même source) et
  émet l'événement SSE `citation`. Les sources citées font foi pour l'affichage (`used` en repli) ; numéro persisté en
  fin de `chat_history.sources` et table des refs dans la trace. Les marqueurs sont retirés de l'historique envoyé au LLM.
- **Accès aux sources** (F2b, `actions/sourceview`) : `GET /api/source-documents/{id}` (métadonnées, extrait, mode
  d'affichage) et `/{id}/file` (PDF `inline`, sinon `attachment` ; `nosniff`, `CSP: sandbox`, `no-store`), où `id` est
  le `documentId` d'une source (chunk, pièce jointe ou processus). Accès : chunk public ou personnel de l'utilisateur,
  pièce jointe de ses conversations, processus ; fichier public réservé aux utilisateurs internes. Inconnu ou
  inaccessible → 404 (indistinguables). `/api/sources/{name}` désigne les sources de workspace. Tout accès à un
  `temp_source_document` passe par le propriétaire (`user_uuid`) : jamais de recherche par nom de fichier seul.

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
