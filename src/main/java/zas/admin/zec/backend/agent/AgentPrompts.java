package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.prompt.PromptTemplate;

public class AgentPrompts {
    private static final String AGENT_SELECTION_PROMPT_FR = """
            <instructions>
                <instruction>sélectionnez l'agent approprié pour répondre à la question de l'utilisateur en fonction des métadonnées ci-dessous et des <agents> à disposition</instruction>
            </instructions>
            
            <agents>
                CHAT_AGENT: résumer une conversation
                CHAT_AGENT: traduire une conversation
                CHAT_AGENT: mettre à jour les préférences utilisateur
            
                RAG_AGENT: questions factuelles simples
                RAG_AGENT: questions multipartites (plusieurs sous questions)
                RAG_AGENT: questions générales relatives à l'AVS/AI
            </agents>
            
            <exemples>
                Résume moi la conversation -> CHAT_AGENT
                Résume moi le dernier message de notre discussion -> CHAT_AGENT
                Traduis cette conversation en allemand -> CHAT_AGENT
                Traduis ce message en italien -> CHAT_AGENT
                Update ma préférences de détail de réponse à très détaillé -> CHAT_AGENT
                Mets à jour mes préférences de recherche à fedlex -> CHAT_AGENT
                Quels sont les changements récents de la législation AVS? -> RAG_AGENT
                Peux-tu m'expliquer les conditions d'octroi d'une rente AI? -> RAG_AGENT
                Comment fonctionne le système de bonus-malus dans l'AVS? -> RAG_AGENT
            </exemples>
            
            <question>
                %s
            </question>
            
            <historique_de_conversation>
                %s
            </historique_de_conversation>
            """;

    private static final String AGENT_SELECTION_PROMPT_DE = """
            <anweisungen>
                <anweisung>wähle den geeigneten Agenten aus, um die Frage des Nutzers auf der Grundlage der unten stehenden Metadaten und der zur Verfügung stehenden <agents> zu beantworten</anweisung>
            </anweisungen>
            
            <agents>
                CHAT_AGENT: eine Unterhaltung zusammenfassen
                CHAT_AGENT: Eine Unterhaltung übersetzen
                CHAT_AGENT: Aktualisieren der Benutzereinstellungen
            
                RAG_AGENT: einfache Sachfragen
                RAG_AGENT: Mehrteilige Fragen (mehrere Unterfragen)
                RAG_AGENT: Allgemeine Fragen zur AHV/IV
            </agents>
            
            <beispiele>
                Fasse die Konversion zusammen -> CHAT_AGENT
                Fasse die letzte Nachricht unserer Diskussion zusammen -> CHAT_AGENT
                Übersetze diese Unterhaltung ins Deutsche -> CHAT_AGENT
                Übersetze diese Nachricht ins Italienische -> CHAT_AGENT
                Aktualisiert meine Antwortdetailpräferenzen auf sehr detailliert -> CHAT_AGENT
                Aktualisiert meine Suchpräferenzen auf fedlex -> CHAT_AGENT
                Was sind die jüngsten Änderungen in der AHV-Gesetzgebung? -> RAG_AGENT
                Kannst du mir die Anspruchsvoraussetzungen für eine IV-Rente erklären? -> RAG_AGENT
                Wie funktioniert das Bonus-Malus-System in der AHV? -> RAG_AGENT
            </beispiele>
            
            <frage>
                %s
            </frage>
            
            <geschichte_der_konversation>
                %s
            <geschichte_der_konversation>
            """;

    private static final String AGENT_SELECTION_PROMPT_IT = """
            <istruzioni>
                <istruzione>selezionare l'agente appropriato per rispondere alla domanda dell'utente in base ai metadati sottostanti e agli <agenti> disponibili</istruzione>
            </istruzioni>
            
            <agenti>
                CHAT_AGENT: riassume una conversazione
                CHAT_AGENT: traduce una conversazione
                CHAT_AGENT: aggiornamento delle preferenze dell'utente
            
                RAG_AGENT: domande semplici e concrete
                RAG_AGENT: domande in più parti (diverse sotto-domande)
                RAG_AGENT: domande generali sull'AVS/AI
            </agenti>
            
            <esempi>
                Riassumere la conversione -> CHAT_AGENT
                Riassumi per me l'ultimo messaggio della nostra discussione -> CHAT_AGENT
                Traduci questa conversazione in tedesco -> CHAT_AGENT
                Traduci questo messaggio in italiano -> CHAT_AGENT
                Aggiornare le preferenze di dettaglio della risposta a Molto dettagliato -> CHAT_AGENT
                Aggiornare le preferenze di ricerca a fedlex -> CHAT_AGENT
                Quali sono i recenti cambiamenti nella legislazione AVS? -> RAG_AGENT
                Puoi spiegarmi i requisiti per l'ottenimento di una rendita AI? -> RAG_AGENT
                Come funziona il sistema bonus-malus nell'AVS? -> RAG_AGENT
            </esempi>
            
            <domanda>
                %s
            </domanda>
            
            <storia_conversazionale>
                %s
            <storia_conversazionale>
            """;

    private static final PromptTemplate MODULE_EXPLANATION_FR = new PromptTemplate("""
            Tu es un agent d’assistance expert pour l’AVS/AI.
            L’objectif de cette conversation était de déterminer un système de rente à utiliser et de calculer le salaire exigible.
            Pour rappel voici ce que tu as déjà fait :
                Décision:
                {decision}
            
                Calcul:
                {calculation}
            
            Tu dois maintenant répondre aux intérrogations de l’utilisateur (qui est un gestionnaire AI) et lui fournir l’aide demandée.
            Notamment sur l’aide à la formulation d’une réponse à l’assuré, tu peux utiliser la fonction module_explanation (si requis par l’utilisateur).
            En t’inspirant des exemples tu peux proposer une réponse synthétique des informations obtenues précédemment.
            """);

    private AgentPrompts() {}

    public static String getAgentSelectionPrompt(String language) {
        return switch (language) {
            case "fr" -> AGENT_SELECTION_PROMPT_FR;
            case "it" -> AGENT_SELECTION_PROMPT_IT;
            default -> AGENT_SELECTION_PROMPT_DE;
        };
    }

    public static PromptTemplate getModuleExplanationPrompt() {
        return MODULE_EXPLANATION_FR;
    }
}
