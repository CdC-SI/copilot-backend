# Documentation technique – Module « Agent Invalidité »

Cette documentation couvre **quatre** classes Java qui collaborent pour permettre à un LLM de guider l’utilisateur dans le calcul d’une rente d’assurance‑invalidité :

| Fichier                       | Rôle principal                                                                                                |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------- |
| **IIAgent.java**              | Orchestration de la conversation et exposition du *tool* à Spring AI.                                         |
| **IIAdvisor.java**            | Pré‑ et post‑traitement du prompt via la chaîne d’advisors ; gère la logique d’étapes et l’arbre de décision. |
| **IITools.java**              | Implémentation du *tool* `invalidity_rate_calculation`, pont vers la logique « legacy ».                      |
| **InvalidityRateSystem.java** | Arbre de décision codé en JSON pour déterminer le système de rente (linéaire / palier).                       |

---

## 1. IIAgent.java

### 1.1. Responsabilité

`IIAgent` implémente l’interface `Agent` et agit comme point d’entrée unique pour traiter une question utilisateur. Il :

1. Convertit l’historique de conversation en objets `UserMessage`/`AssistantMessage`.
2. Injecte un **Tool** (`IITools`) et un **Advisor** (`IIAdvisor`) dans le *prompt builder* de Spring AI.
3. Retourne un `Flux<Token>` au front‑end, où chaque `Token` représente soit :
    - du texte à afficher (`TextToken`),
    - soit une suggestion de prochaine action (`SuggestionToken`).

### 1.2. API publique

| Méthode                                                               | Description                                                         |
| --------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `String getName()`                                                    | Nom logique de l’agent : **"AI\_AGENT"**.                           |
| `AgentType getType()`                                                 | Enum : retourne `II_AGENT`.                                         |
| `Flux<Token> processQuestion(Question, String userId, List<Message>)` | Exécute l’enchaînement Chat → Tools → Advisors et émet les `Token`. |

### 1.3. Détails d’implémentation

```java
client.prompt()
      .messages(convert(history))
      .user(question.query())
      .tools(new IITools(holder, convId))
      .advisors(new IIAdvisor(holder, convId, model))
      .stream()
```

- Chaque réponse du modèle est convertie via `convertToToken(...)` :
    - métadonnée `suggestion` → `SuggestionToken` ;
    - sinon texte brut → `TextToken`.

---

## 2. IITools.java

### 2.1. Responsabilité

Fournit au LLM l’outil ``, annoté avec `@Tool`. Le LLM peut l’appeler en langage naturel ; Spring AI effectue alors le binding des paramètres.

### 2.2. Signature du tool

```java
String invalidityRateCalculation(int yearOfEligibility,
                                 String gender,
                                 double preHealthEffectiveSalary,
                                 int preHealthEffectiveYear,
                                 int preHealthSkillLevel,
                                 String preHealthEconomicBranch,
                                 double postHealthEffectiveSalary,
                                 int postHealthEffectiveYear,
                                 int postHealthSkillLevel,
                                 String postHealthEconomicBranch,
                                 int activityRate,
                                 int reduction,
                                 int deduction)
```

*Retour :* montant du salaire exigible calculé.

### 2.3. Pipeline interne

1. Construction des DTO `EffectiveSalaryInfo` & `StatisticalSalaryInfo`.
2. Création d’un `Beneficiary` agrégant toutes les données.
3. Appel à la méthode **legacy** `IncomeCalculation.getInvalidite(...)`.
4. Réinitialisation des métadonnées de conversation (`holder.clearMetaData`) en cas de succès.

### 2.4. Gestion des erreurs

- En cas d’exception, le stack‑trace est journalisé (`log.error`) et la métadonnée reste intacte (permet un retry).

---

## 3. IIAdvisor.java

### 3.1. Responsabilité

Intercepte le flux de messages pour :

1. **Étape 1** : déterminer / collecter les infos nécessaires (QA) jusqu’à décision du système.
2. **Étape 2** : laisser le LLM appeler le *tool* puis signaler le front‑end via un `SuggestionToken`.

### 3.2. Cycle de vie

| Méthode                  | Rôle                                                                                                                   |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| `before(AdvisedRequest)` | Prépare le prompt selon l’étape courante ; appelle `convertPromptStream` pour extraire les QA et mettre à jour l’état. |
| `after(AdvisedResponse)` | Si l’étape = 2 et la réponse est complète, remplace la réponse par une suggestion `ii‑salary`.                         |
| `aroundStream(...)`      | Compose `before` → modèle → `after`.                                                                                   |

### 3.3. Extraction de QA (`convertPromptStream`)

1. Construit un nouveau `Prompt` avec le **CONVERT\_PROMPT** (instructions d’extraction).
2. Utilise un `BeanOutputConverter` pour décoder la liste `[Qa]` JSON renvoyée par le modèle.
3. Stocke la liste dans le `ConversationMetaDataHolder`.
4. Appelle `InvalidityRateSystem.getDecision(...)` :
    - **match complet** → passe `etape` = 2.
    - **match partiel** → renvoie la « Question suivante suggérée ».

### 3.4. Gestion de l’état (via `ConversationMetaDataHolder`)

- `currentAgentInUse` : qui parle ?
- `etape` : 1 ou 2.
- `answeredQuestions` : liste de QA déjà identifiées.

---

## 4. InvalidityRateSystem.java

### 4.1. Responsabilité

Déterminer, à partir d’une liste de réponses QA, quel **système de rente** s’applique et citer les sources légales.

### 4.2. Données embarquées

- Constante JSON `ALL_PATHS` : tableau de **204** chemins (exemple) ; chaque chemin décrit :
    - `path` : séquence exhaustive de QA requises ;
    - `answer` : `{ decision, sources[] }`.

### 4.3. Algorithme `getDecision(info)`

```pseudo
if info est vide
    return « Plus d’informations… »
for each path in ALL_PATHS
    if info couvre entièrement path.path
        return Décision + Sources
// Sinon : chercher le meilleur chemin partiel et suggérer la 1ʳᵉ QA manquante
return « Question suivante suggérée : … »
```

### 4.4. Classes internes

| Classe        | Description                                                                       |
| ------------- | --------------------------------------------------------------------------------- |
| `Qa` (record) | Couple *(question, answer)* avec `equals` redéfini pour faciliter la comparaison. |
| `Answer`      | Décision + sources.                                                               |
| `Path`        | Liste de `Qa` + `Answer`.                                                         |

---

## 5. Séquence d’exécution simplifiée

```mermaid
sequenceDiagram
  participant User
  participant IIAgent
  participant IIAdvisor
  participant ChatModel
  participant IITool

  User->>IIAgent: Question
  IIAgent->>IIAdvisor: before()
  IIAdvisor->>ChatModel: prompt étape 1/2
  ChatModel-->>IIAdvisor: réponse
  IIAdvisor->>IIAdvisor: after()
  IIAdvisor-->>IIAgent: ChatResponse
  IIAgent-->>User: Token(s)
  Note over IITool,IIAdvisor: Si étape 2 → ChatModel appelle le tool,
    IITool calcule et retourne le résultat.
```

---

### 6. Points d’extension pour le développeur

- **Robustesse de l’extraction QA** : la méthode `convertPromptStream` repose sur un prompt pour extraire les couples *question/réponse*. Cette extraction reste perfectible ; certaines négations ou reformulations échappent encore à l’algorithme.

- **Performance** : `convertPromptStream` déclenche un appel LLM supplémentaire et peut durer plusieurs secondes. Une mise en cache ou un traitement totalement asynchrone est souhaitable.

- **Amélioration des prompts** : les prompts actuels (`CONVERT_PROMPT`, `ROUTING_PROMPT`, etc.) peuvent être épurés, fournir des exemples négatifs et/ou exploiter `json_schema` pour fiabiliser la sortie.

- **Persistance de l’arbre de décision** : le JSON volumineux embarqué dans `InvalidityRateSystem` doit être migré dans une base de données (ex. table `decision_paths`) afin de faciliter la maintenance et permettre la consultation analytique.

- **Module de sortie finale** : il n’existe pas encore de pipeline convertissant le résultat de l’étape 2 en livrable (PDF, note interne, API externe). À définir : interface `OutputFormatter` + implémentations.

- **Formatage / richesse de l’étape 2** : la réponse générée après le calcul est très succincte et parfois mal formatée (sauts de ligne, décimales). Elle devrait détailler les intermédiaires du calcul et retourner un markdown/tableau lisible.

- **Tests automatisés** : ajouter des tests d’intégration bout‑en‑bout pour valider l’extraction QA, le temps d’exécution et la conformité des décisions.

