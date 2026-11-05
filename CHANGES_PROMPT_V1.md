# Prompt agentique v1 et processus liés : notes de revue

Branche `feature/document-tool_updated`, construite sur `feature/document-tool`.
Ce document sert à la revue de code : il dit ce qui change, pourquoi, et où regarder.

## Résumé

- **Le problème.** L'agent voyait les processus métier liés aux aide-mémoire sans les lire, ou sautait une décision du processus qui contredisait l'aide-mémoire. Il supposait parfois un fait absent (l'âge, par exemple) au lieu de le demander. L'ancien prompt agentique (environ 25 000 caractères) contenait aussi des règles qui se contredisaient.
- **Ce qui change.**
  - Le processus lié est joint automatiquement au résultat de recherche, puis au tour suivant.
  - Un nouveau prompt `v1`, deux fois plus court et sans contradiction, impose une méthode : confronter les sources, ne jamais supposer, demander. Il impose aussi une structure de réponse fixe.
  - Le reasoning et la température de l'appel agent sont réglables.
- **Valeurs par défaut** : reasoning `medium`, température `1.0`. Elles sont justifiées par la mesure en fin de document.
- **Aucune migration de base.** Seul prérequis : la table `business_process` doit contenir les processus au format `next` (voir « Prérequis »).

## Inventaire des fichiers

Chemins relatifs à `src/main/java/zas/admin/zec/backend/` (code) et `src/test/java/zas/admin/zec/backend/` (tests).

| Fichier | Statut | Rôle | À regarder en revue |
|---|---|---|---|
| `tools/LinkedProcessAttacher.java` | ajouté | Lit le processus lié (version en vigueur) et le rend dans un bloc `<linked_processes>`, à la recherche et au tour suivant | Limites (nombre, taille), accès réservé aux utilisateurs internes, échec de lecture → rien de joint |
| `config/properties/LinkedProcessProperties.java` | ajouté | Réglages `rag.linked-processes.*` | Valeurs par défaut dans `application.yml` |
| `config/properties/AgentProperties.java` | ajouté | Réglages `rag.agent.*` (reasoning, température), validés au démarrage | La liste des valeurs de reasoning acceptées |
| `tools/RAGTool.java` | modifié | Ajoute au résultat de recherche `<linked_sources>` (cibles des liens pas encore lues) et `<linked_processes>` (processus joint) | `linkedSources()` : ordre, dédoublonnage, limite par collection ; ordre des blocs dans le résultat |
| `actions/converse/RAGChatService.java` | modifié | Applique les réglages à l'appel agent seulement ; rejoint au tour suivant le processus utilisé ; trace les réglages | `agentOptions()`, `processesFromPreviousTurn()`, `agentSettings()` |
| `rag/RAGPrompts.java` | modifié | Prompt agentique `v1` en FR, DE et IT, à la place de l'ancien | Le texte lui-même (voir « Prompt v1 ») ; les autres prompts du fichier sont inchangés |
| `rag/TurnUsage.java` | modifié | Nouveau champ `agent_settings` dans le JSON `chat_history.usage` | Constructeur à deux arguments conservé (compatibilité) |
| `tools/DocumentTool.java` | modifié | Description de l'outil : l'appeler directement quand l'utilisateur nomme un processus ou un document | Texte seulement, aucun changement de comportement |
| `src/main/resources/application.yml` | modifié | Blocs `rag.agent` et `rag.linked-processes` | Valeurs par défaut |
| `tools/LinkedProcessAttacherTest.java` | ajouté (test) | Couvre `LinkedProcessAttacher` | |
| `rag/RAGPromptsTest.java` | ajouté (test) | Contrat du prompt dans les trois langues, règles de `v1` | Voir « Tests » |
| `tools/RAGToolTest.java` | modifié (test) | Blocs `<linked_sources>` et `<linked_processes>` | |
| `actions/converse/RAGChatServiceTest.java` | modifié (test) | Constructeur du service, processus rejoint au tour suivant, réglages de l'agent | |
| `CHANGES_PROMPT_V1.md` | ajouté | Ce document | |

**Supprimé** : aucun fichier. L'ancien prompt agentique (`AGENTIC_SYSTEM_PROMPT_FR`, `_DE`, `_IT`) est entièrement remplacé, et aucun autre prompt de `RAGPrompts` ne change.

## Les modifications, une par une

### 1. Le processus lié est joint d'office

- **Avant** : un aide-mémoire renvoyait à un processus par un lien (`outgoing_links[].process_id`). L'agent devait penser à appeler `get_documents` pour le lire. Sur l'évaluation AFAC, il ne le faisait que dans 62 % des tours concernés.
- **Après** :
  - `RAGTool` passe les cibles des liens à `LinkedProcessAttacher.forSearch()`.
  - Celui-ci lit le premier processus lié dans la version en vigueur (`BusinessProcessLookup`) et l'ajoute **à la fin** du résultat de recherche, dans `<linked_processes>`. La fin a été choisie pour que le modèle ne le survole pas après de longs extraits.
  - Le processus joint compte comme document lu : il apparaît dans les sources consultées et sort de `<linked_sources>`.
  - Au **tour suivant**, `RAGChatService.processesFromPreviousTurn()` rejoint le processus quand l'utilisateur répond à une question :
    - après un `followup`, tous les processus lus au tour précédent ;
    - après un `answer`, ceux que la réponse déclare dans `used` ;
    - après un `refusal`, aucun.
- **Garde-fous** :
  - au plus `RAG_LINKED_PROCESSES_MAX` processus (défaut 1) et `RAG_LINKED_PROCESSES_MAX_CHARS` caractères (défaut 20 000) ;
  - utilisateurs internes seulement ;
  - en cas d'erreur de lecture, rien n'est joint et la réponse continue ;
  - interrupteur `RAG_LINKED_PROCESSES_ENABLED`.
- **Tests** : `LinkedProcessAttacherTest`, `RAGToolTest.linkedProcessIsAttachedToTheSearchResultAndLeavesLinkedSources`, `RAGChatServiceTest.processReadBeforeAFollowUpQuestion…`, `…processUsedByThePreviousAnswer…`, `…noProcessIsAttachedAfterAnAnswer`.

### 2. `<linked_sources>` : les cibles des liens pas encore lues

- **Avant** : les liens restaient enfouis dans les métadonnées de chaque extrait.
- **Après** : à la fin du résultat de recherche, une liste des processus et documents liés aux extraits et pas encore lus pendant le tour, sans doublon et dans l'ordre de pertinence. Chaque cible est donnée avec la collection et le filtre à passer à `get_documents`. Il y a au plus 5 cibles par collection, soit un seul appel avec un filtre `IN`.
- **Tests** : `RAGToolTest.linkedSources…`, `targetsAlreadyReadDuringTheTurnAreExcluded`, `blockListsTargetsAsDataWithoutInstruction`.

### 3. Prompt agentique `v1` (FR, DE, IT)

Il fait environ 12 000 caractères, contre 25 000 pour l'ancien, et compte dix sections : rôle, sécurité, sources, outils, blocs système, méthode, processus, réponse, fin de tour, format de réponse. Le principe d'écriture : **une règle n'est écrite qu'à un seul endroit.**

- **Méthode pour un cas concret** :
  1. chercher ;
  2. lire les sources liées ;
  3. confronter chaque condition et chaque décision du processus ;
  4. si un fait manque ou si les sources divergent : ne pas conclure, dire ce qui est établi, citer chaque source, poser **une seule** question ;
  5. sinon, conclure en nommant la condition décisive.
- **Règle des écarts, validée avec le métier** : il y a écart quand deux sources disent autre chose sur le même point. Le **silence** d'une source n'est pas un écart : une condition donnée par une seule source s'applique, et la réponse signale que l'autre source ne la mentionne pas.
- **Processus** :
  - le prompt décrit le format JSON `next` (`nodes`, `next` avec `if`, `"decision": true`, `"loop": true`, `default_role`) ;
  - l'agent suit le flux, s'arrête à la première décision dont le fait manque, et reprend au tour suivant ;
  - il parle comme les gestionnaires : jamais « nœud », « gateway » ni `node_3`.
- **Structure de réponse imposée pour un cas** :
  - titre `## Oui, …`, `## Non, …` ou `## À compléter : …` ;
  - tableau « Conditions vérifiées » dont la colonne Résultat contient exactement ✅ Remplie, ⛔ Non remplie, ❓ À vérifier ou ⚠️ Écart entre les sources ;
  - « Point à préciser » ou « Suite du traitement » ;
  - une ligne « **Question :** » à la fin s'il reste un point ouvert.
- **Allemand et italien** : même structure. Les sections, les libellés et les symboles sont traduits (`Ja / Nein / Zu ergänzen`, `Sì / No / Da completare`, `**Frage:**`, `**Domanda:**`). Les noms d'outils, de blocs et de clés JSON ne sont pas traduits. La consigne de domaine garde le nom que le code injecte (`<vorrangige_anweisung>`, `<istruzione_prioritaria>`).

Contradictions de l'ancien prompt, et comment `v1` les tranche :

| | Règle A de l'ancien prompt | Règle B qui la contredisait | Dans `v1` |
|---|---|---|---|
| C1 | Documents contradictoires → « documentation insuffisante » (refus) | Écart processus / document → signaler et demander | Signaler et demander ; le refus est réservé à « rien de pertinent trouvé » |
| C2 | Documents insuffisants ou contradictoires → le dire, question « si pertinent » | C1 | Supprimée (couverte par `<sources>` et `<methode>`) |
| C3 | Réponse partielle + question → `answer` | Écart qui change l'issue → `followup` | Un seul critère, dans `<fin_de_tour>` : `answer` dès qu'il y a du contenu de fond |
| C4 | « Soyez décisif, une seule option » | « Ne tranchez pas, demandez » | Supprimée |
| C5 | « 1 à 3 phrases » | « Exposez, citez, demandez » | Concis, sans retirer ce que la méthode exige |
| C6 | « Évitez les tableaux » | « Markdown (listes, tableaux) » | Markdown sobre ; le tableau des conditions est imposé pour un cas |
| C7 | Question de suivi « sans en abuser » | Question obligatoire sur une décision ouverte | La retenue ne vaut que pour l'intention de la demande, pas pour les faits manquants |
| C8 | « Quels sont tes outils ? » = attaque | Répondre sur ses capacités | Capacités : oui ; fonctionnement interne : refus |
| C9 | Lire le processus lié avec `get_documents` | `<linked_processes>` déjà lu | Une seule phrase : déjà lu, ne pas relire |

### 4. Réglages de l'appel agent

- **Avant** : `temperature(0.0)` et `reasoningEffort("low")` étaient codés en dur dans `ChatModelsConfig`, pour tous les appels au modèle.
- **Après** :
  - `RAGChatService.agentOptions()` applique `RAG_AGENT_REASONING_EFFORT` et `RAG_AGENT_TEMPERATURE` **à l'appel agent seulement**. Les appels internes (choix de l'espace, réécriture de requête) gardent leurs réglages ; `ChatModelsConfig` n'est pas modifié.
  - Les réglages effectifs et la version du prompt sont écrits dans `chat_history.usage` sous `agent_settings`, pour comparer les configurations à l'évaluation.
  - Une valeur de reasoning invalide, par exemple suivie d'un commentaire dans le `.env`, **bloque le démarrage** au lieu de faire échouer chaque question.
- **Tests** : `RAGChatServiceTest.agentSettingsApplyToTheAgentCallAndAreTraced`, `invalidReasoningEffortFailsAtStartup`.

### 5. Description de `get_documents`

Une phrase ajoutée : appeler l'outil directement, sans recherche préalable, quand l'utilisateur désigne un processus par son nom ou son identifiant, ou un document par son identifiant. La recherche sémantique ne retrouve pas un document par son identifiant.

## Variables d'environnement

Toutes sont nouvelles et facultatives. Comme les autres variables du backend, elles ne sont nommées que dans
`src/main/resources/application.yml` (`${VARIABLE:défaut}`). Spring les range dans une propriété `rag.*`, que le
code lit à travers une classe de configuration : le nom de la variable n'apparaît donc pas dans le code Java.

| Variable | Défaut | Propriété (`application.yml`) | Lue par | Effet |
|---|---|---|---|---|
| `RAG_AGENT_REASONING_EFFORT` | `medium` | `rag.agent.reasoning-effort` | `AgentProperties.reasoningEffort()` | Effort de raisonnement de l'appel agent (`none`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max`) |
| `RAG_AGENT_TEMPERATURE` | `1.0` | `rag.agent.temperature` | `AgentProperties.temperature()` | Température de l'appel agent (0 à 2) ; 1.0 est la valeur recommandée par la fiche du modèle en mode thinking |
| `RAG_LINKED_PROCESSES_ENABLED` | `true` | `rag.linked-processes.enabled` | `LinkedProcessProperties.enabled()` | Jointure automatique des processus liés |
| `RAG_LINKED_PROCESSES_MAX` | `1` | `rag.linked-processes.max-processes` | `LinkedProcessProperties.maxProcesses()` | Processus joints au plus par recherche ou par tour |
| `RAG_LINKED_PROCESSES_MAX_CHARS` | `20000` | `rag.linked-processes.max-chars` | `LinkedProcessProperties.maxChars()` | Taille cumulée maximale des processus joints |

Dans le `.env`, aucun commentaire en fin de ligne : il serait lu comme une partie de la valeur.

## Prérequis et compatibilité

- **Format des processus.** Le prompt `v1` décrit le format `next` de la colonne `business_process.content`. Il est produit par le preprocessing (dépôt preprocessing, branche `preprocessing_docling_enhance`, commande `afac-preprocess processes export`). Un ancien chargement au format `{"nodes", "arcs"}` reste lisible, mais le prompt ne le décrit plus : il faut recharger la table.
- **Base de données** : aucune migration ; `usage` est un JSON libre.
- **Sans variable dans le `.env`** : les valeurs par défaut ci-dessus s'appliquent.
- **API et frontend** : aucun changement.

## Scripts associés (hors de ce dépôt)

| Dépôt | Script | Rôle |
|---|---|---|
| preprocessing | `afac-preprocess processes extract` | Export BPMN (BPanda) → JSON des processus, sans appel au modèle |
| preprocessing | `afac-preprocess processes export` | JSON → `business_process.csv` (format `next`) et `process_descriptions.csv` (descriptions et embeddings) |
| preprocessing | `afac-preprocess check` | Contrôle des CSV, dont le format `next` et la cohérence documents ↔ processus |
| ai-evals | suite AFAC, `score_strict.py` | Évaluation (58 cas, 68 tours) : verdict du harness et du juge, temps, coût, espace détecté |

La procédure pas à pas est dans le `README.md` du dépôt preprocessing.

## Comment tester

1. `mvn test`, ou `sh mvnw test` (le script `mvnw` n'est pas exécutable dans le dépôt, donc `./mvnw` échoue dans un clone neuf) : 236 tests, 0 échec.
2. Backend lancé avec les processus au format `next` en base, trois questions dans le frontend :
   - FR : « Un homme suisse, établi au Tibet depuis 2 mois et immatriculé, a été assuré sans interruption pendant les 12 années précédant son départ. Peut-il adhérer à l'AVS/AI facultative ? »
   - DE : « Ein Schweizer, seit 2 Monaten in Tibet niedergelassen und immatrikuliert, war während der 12 Jahre vor seiner Ausreise ununterbrochen versichert. Kann er der freiwilligen AHV/IV beitreten? »
   - IT : « Un uomo svizzero, stabilito in Tibet da 2 mesi e immatricolato, è stato assicurato senza interruzione per i 12 anni precedenti la sua partenza. Può aderire all'AVS/AI facoltativa? »

   Attendu, dans la langue de la question :
   - un titre « À compléter » ;
   - un tableau des conditions avec ✅ et ❓ ;
   - une question finale qui demande l'âge, car c'est une décision du processus ;
   - le processus d'adhésion dans les sources.
3. Dans `chat_history.usage` du dernier message : `"agent_settings": {"prompt_version": "v1", "reasoning_effort": "medium", "temperature": 1.0}`.

## Mesure : `low` contre `medium`

Évaluation du 04.11, avec le prompt `v1` et une température de 1.0. Elle porte sur 31 tests (cas d'adhésion, pièges à fait manquant, règles), chacun passé 3 fois.

| | `low` | `medium` |
|---|---|---|
| Erreurs critiques (une question attendue n'est pas posée) | 46 % | **26 %** |
| Tests instables entre les 3 passages | 35 % | **10 %** |
| Premier mot affiché, médiane / 95e centile | 11,8 s / 23,8 s | **10,4 s / 18,7 s** |
| Durée d'une conversation, médiane | 25,1 s | **20,6 s** |
| Jetons par conversation, médiane | 38 300 | 48 200 (+26 %) |

`medium` est plus fiable et n'est pas plus lent ; seul le nombre de jetons augmente.

**Limite connue, hors périmètre** : environ 12 % des questions AFAC sont aiguillées vers un mauvais espace documentaire (CSC, LOIS_AVS…), ce qui mène souvent à un refus. Ce problème concerne le choix de l'espace, pas ce changement.
