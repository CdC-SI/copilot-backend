package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.prompt.PromptTemplate;

public class AgentPrompts {
    private static final String AGENT_SELECTION_PROMPT_FR = """
            <instructions>
                <instruction>sélectionnez l'agent approprié pour répondre à la question de l'utilisateur en fonction des métadonnées ci-dessous et des <agents> à disposition</instruction>
            </instructions>
            
            <format_de_réponse>
            AgentHandoff(
                agent: String # le nom de l'agent approprié pour répondre à la question.
            )
            </format_de_réponse>
            
            <agents>
                CHAT_AGENT: résumer une conversation
                CHAT_AGENT: traduire une conversation
                CHAT_AGENT: mettre à jour les préférences utilisateur
            
                RAG_AGENT: questions factuelles simples
                RAG_AGENT: questions multipartites (plusieurs sous questions)
                RAG_AGENT: questions générales relatives à l'AVS/AI
            
                PENSION_AGENT: questions sur le calcul du taux de réduction lié au départ à la retraite
                PENSION_AGENT: questions sur le calcul de supplément de rente lié au départ à la retraite

                II_AGENT: questions sur le calcul de la rente AI
                II_AGENT: questions sur l'atribution de rentes AI
            </agents>
            
            <exemples>
                Résume moi la converation -> CHAT_AGENT
                Résume moi le dernier message de notre discussion -> CHAT_AGENT
                Traduis cette conversation en allemand -> CHAT_AGENT
                Traduis ce message en italien -> CHAT_AGENT
                Update ma préférences de détail de réponse à très détaillé -> CHAT_AGENT
                Mets à jour mes préférences de recherche à fedlex -> CHAT_AGENT
                Je suis née le 1962.31.12, je souhaite prendre ma retraite le 01.01.2025 et mon revenu annuel est d'environ 55'000 CHF. Quel est mon taux de réduction ? -> PENSION_AGENT
                Quel sera mon taux de réduction si je suis née le 1965-11-07, je souhaite prendre ma retraite le 2026-04-15 et mon revenu annuel est de 76200 ? -> PENSION_AGENT
                Voici mes informations: date de naissance le 03.01.1968 et je pars à la retraite en 2027. Je gagne environ 90000 CHF par an. Puis-je bénéficier d'un supplément ou taux de réduction ? -> PENSION_AGENT
                J'ai besoin d'aide pour calculer la rente AI d'un bénéficiaire. -> II_AGENT
                Je souhaite calculer mon salaire exigible, je bénéficie d’une rente invalidité. -> II_AGENT
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
            
            <format_der_antwort>
            AgentHandoff(
                agent: String # der Name des entsprechenden Agenten, der die Frage beantwortet.
            )
            </format_der_antwort>
            
            <agents>
                CHAT_AGENT: eine Unterhaltung zusammenfassen
                CHAT_AGENT: Eine Unterhaltung übersetzen
                CHAT_AGENT: Aktualisieren der Benutzereinstellungen
            
                RAG_AGENT: einfache Sachfragen
                RAG_AGENT: Mehrteilige Fragen (mehrere Unterfragen)
                RAG_AGENT: Allgemeine Fragen zur AHV/IV
            
                PENSION_AGENT: Fragen zur Berechnung des Kürzungssatzes bei der Pensionierung
                PENSION_AGENT: Fragen zur Berechnung des Rentenzuschlags bei der Pensionierung
            </agents>
            
            <beispiele>
                Fasse die Konversion zusammen -> CHAT_AGENT
                Fasse die letzte Nachricht unserer Diskussion zusammen -> CHAT_AGENT
                Übersetze diese Unterhaltung ins Deutsche -> CHAT_AGENT
                Übersetze diese Nachricht ins Italienische -> CHAT_AGENT
                Aktualisiert meine Antwortdetailpräferenzen auf sehr detailliert -> CHAT_AGENT
                Aktualisiert meine Suchpräferenzen auf fedlex -> CHAT_AGENT
                Ich bin am 1962.31.12 geboren, möchte am 01.01.2025 in Rente gehen und mein Jahreseinkommen beträgt ca. 55'000 CHF. Wie hoch ist mein Kürzungssatz? -> PENSION_AGENT
                Wie hoch ist mein Kürzungssatz, wenn ich am 1965-11-07 geboren bin, am 2026-04-15 in Rente gehen möchte und mein Jahreseinkommen 76200 beträgt? -> PENSION_AGENT
                Hier sind meine Informationen: Geburtsdatum 03.01.1968 und ich gehe 2027 in Rente. Ich verdiene etwa 90000 CHF pro Jahr. Kann ich einen Zuschlag oder einen Kürzungssatz erhalten? -> PENSION_AGENT
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
            
            <risposta_formato>
            AgentHandoff(
                agent: String # il nome dell'agente appropriato per rispondere alla domanda
            )
            </risposta_formato>
            
            <agenti>
                CHAT_AGENT: riassume una conversazione
                CHAT_AGENT: traduce una conversazione
                CHAT_AGENT: aggiornamento delle preferenze dell'utente
            
                RAG_AGENT: domande semplici e concrete
                RAG_AGENT: domande in più parti (diverse sotto-domande)
                RAG_AGENT: domande generali sull'AVS/AI
            
                PENSION_AGENT: domande sul calcolo del tasso di riduzione al momento del pensionamento
                PENSION_AGENT: domande sul calcolo del supplemento di pensione al momento del pensionamento
            </agenti>
            
            <esempi>
                Riassumere la conversione -> CHAT_AGENT
                Riassumi per me l'ultimo messaggio della nostra discussione -> CHAT_AGENT
                Traduci questa conversazione in tedesco -> CHAT_AGENT
                Traduci questo messaggio in italiano -> CHAT_AGENT
                Aggiornare le preferenze di dettaglio della risposta a Molto dettagliato -> CHAT_AGENT
                Aggiornare le preferenze di ricerca a fedlex -> CHAT_AGENT
                Sono nato il 31.12.1962, voglio andare in pensione il 01.01.2025 e il mio reddito annuo è di circa 55.000 franchi. Qual è il mio tasso di riduzione? -> AGENTE_PENSIONE
                Qual è il mio tasso di riduzione se sono nato il 1965-11-07, voglio andare in pensione il 2026-04-15 e il mio reddito annuo è di CHF 76200? -> AGENTE_PENSIONE
                Ecco le mie informazioni: sono nato il 03.01.1968 e andrò in pensione nel 2027. Guadagno circa 90.000 franchi all'anno. Posso beneficiare di un'integrazione o di una riduzione? -> AGENTE_PENSIONE
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
