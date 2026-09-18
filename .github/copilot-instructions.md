# Instructions Copilot — copilot-backend

Backend Spring Boot du ZAS/EAK-Copilot : assistant RAG (Retrieval-Augmented Generation) pour
les assurances sociales suisses, basé sur Spring AI.

## Stack technique

- **Java 21** (utiliser les fonctionnalités modernes : records, sealed interfaces, pattern matching, `var`, `List.copyOf`, etc.)
- **Spring Boot 3.5.x**, **Spring AI 1.1.x** (`spring-ai-openai`, `spring-ai-rag`, `spring-ai-client-chat`, pgvector)
- **PostgreSQL** + **pgvector**, migrations **Flyway** (`src/main/resources/db/migration`)
- **Maven** (wrapper présent mais voir ci-dessous), **Lombok**, **MapStruct**, **Reactor** (`Flux`/`Mono`)

## Commandes de build / test

- **Utiliser directement `mvn`** (et non `./mvnw` / `mvnw.cmd`) pour toutes les commandes de build et de test.
- Dépôt Maven local : `D:\maven\repository` — déjà configuré (settings.xml), **inutile de le préciser** dans les commandes (`-Dmaven.repo.local=...`).
- **JDK 21 obligatoire** : le Java par défaut de la machine est le JDK 25, **incompatible avec Lombok** sur ce projet (erreurs `cannot find symbol` sur les getters/setters générés). Définir `JAVA_HOME` sur le JDK 21 avant toute commande Maven, par exemple :
  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.1.12-hotspot'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  mvn -o compile
  ```
  (adapter le chemin si l'installation diffère). Comme chaque commande `powershell` démarre un processus neuf, il faut redéfinir `JAVA_HOME`/`Path` dans **chaque** appel Maven.
- Privilégier le mode hors-ligne quand possible : `mvn -o ...`.
- Exemples :
  - Compiler : `mvn -o compile`
  - Lancer les tests : `mvn -o test`
  - Build complet (référence) : `mvn clean install`
  - Build complet hors-ligne : `mvn -o clean package`
  - Un seul test : `mvn -o test -Dtest=NomDeLaClasseTest`
- Shell par défaut : **PowerShell** sous Windows. Chaîner les commandes avec `;` (pas `&&`).

## Conventions de code

- Packages sous `zas.admin.zec.backend` :
  - `actions/` : controllers REST + services applicatifs (converse, upload, authorize, api...)
  - `agent/` : ancienne mécanique multi-agents — **dépréciée** (`@Deprecated(forRemoval = true)`), en cours de remplacement par des **tools Spring AI**.
  - `tools/` : tools Spring AI (`@Tool`) et services utilitaires (ex. `RAGTool`, `TariffService`).
  - `rag/` : pipeline RAG (retriever, reranker, joiner, advisor, prompts, validation, token).
  - `config/`, `persistence/` : configuration et accès données.
- **Tools Spring AI** : annoter les méthodes avec `@Tool` (nom + description claire pour guider le LLM) et `@ToolParam` pour les arguments fournis par le LLM. Les données **non fournies par le LLM** (userId, langue, workspace, conversationId, droits) transitent par le `ToolContext` ; définir les clés comme constantes réutilisables.
- Préférer l'injection par constructeur (pas de `@Autowired` sur les champs).
- Utiliser **Lombok** (`@Slf4j` pour le logging) conformément à l'existant.
- Les DTO/payloads sont des **records** (souvent avec `@Builder` et validation `jakarta.validation`).
- Respecter le style réactif existant (`Flux<Token>`, SSE) dans les flux de conversation.
- Les prompts LLM sont centralisés (ex. `RAGPrompts`) et **multilingues** (fr/de/it) : conserver cette structure.

## Bonnes pratiques

- Après une modification, valider avec `mvn -o compile` (ou les diagnostics IDE) avant de conclure.
- Ne pas casser les chemins publics existants (`PublicChatController`) ni `ConversationService` lors de refactorings progressifs.
- Lors d'un remplacement par étapes, **marquer l'ancien code** `@Deprecated(forRemoval = true)` avec un commentaire expliquant la suite, plutôt que de tout supprimer d'un coup.
- Ne pas committer de secrets ; les profils de config sont dans `src/main/resources/application*.yml`.

