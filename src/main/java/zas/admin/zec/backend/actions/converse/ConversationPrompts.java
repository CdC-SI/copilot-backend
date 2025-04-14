package zas.admin.zec.backend.actions.converse;

public class ConversationPrompts {

    public static final String OFF_TOPIC_MESSAGE_FR = "Comment puis-je vous aider à répondre à vos questions concernant l’AVS/AI ?";
    public static final String OFF_TOPIC_MESSAGE_IT = "Come posso aiutarvi a rispondere alle vostre domande sull'AVS/AI?";
    public static final String OFF_TOPIC_MESSAGE_DE = "Wie kann ich Ihnen bei Ihren Fragen zur AHV/IV helfen?";

    public static final String TOPIC_CHECK_PROMPT_FR = """
            # Tâche
            Votre tâche consiste à évaluer si la question fait partie des sujets ci-dessous.
            
            ## Sujets
            - Assurances sociales
            - Assurance vieillesse
            - Assurance invalidité
            - Assurance chômage
            - Allocations familiales
            - Cotisations AVS/AI/APG/AC
            - Prestations AVS/AI/APG/AC
            - Prestations complémentaires à l'AVS/AI
            - Caisse de compensation
            - Retraite
            - Prévoyance professionnelle
            - Rente AVS/AI
            
            ## Format de réponse
            
            Répondez par true si la question fait partie des sujets ci-dessus, sinon répondez par false.
            
            ## Question
            %s
            """;

    public static final String TOPIC_CHECK_PROMPT_IT = """
            # Compito
            Il vostro compito è quello di valutare se la domanda rientra in uno degli argomenti di seguito elencati.
            
            ## Soggetti
            - Assicurazione sociale
            - Assicurazione di vecchiaia
            - Assicurazione di invalidità
            - Assicurazione contro la disoccupazione
            - Assegni familiari
            - Contributi AVS/AI/APG/AC
            - Prestazioni AVS/AI/APG/AC
            - Prestazioni complementari AVS/AI
            - Cassa di compensazione
            - Pensioni
            - Previdenza professionale
            - Rendita AVS/AI
            
            ## Formato della risposta
            
            Rispondere con True se la domanda è una delle precedenti, altrimenti rispondere con False.
            
            ## Domanda
            %s
            """;

    public static final String TOPIC_CHECK_PROMPT_DE = """
            # Aufgabe
            Ihre Aufgabe ist es, zu beurteilen, ob die Frage zu den unten aufgeführten Themen gehört.
            
            ## Themen
            - Sozialversicherungen
            - Versicherung im Alter
            - Versicherung gegen Invalidität
            - Versicherung gegen Arbeitslosigkeit
            - Familienzulagen
            - AHV/IV/EO/ALV-Beiträge
            - Leistungen der AHV/IV/EO/ALV
            - Ergänzungsleistungen zur AHV/IV
            - Ausgleichskasse
            - Pensionierung
            - Berufliche Vorsorge
            - AHV/IV-Rente
            
            ## Antwortformat
            
            Beantworten Sie die Frage mit True, wenn sie zu den oben genannten Themen gehört, ansonsten mit False.
            
            ## Frage
            %s
            """;
    public static final String CONVERSATION_TITLE_PROMPT_FR = """
            <instructions>
                <instruction>Générez un titre décrivant le sujet de la question de l'utilisateur et de la réponse de l'assistant <réponse_assistant></instruction>
                <instructions>Le titre doit être haut niveau à partir de la question ET de la <réponse_assistant> qui capturera l'essence de la conversation qui s'ensuit</instruction>
                <instructions>Le titre doit être extrêmement concis et informatif (MAXIMUM 4-6 MOTS), et donner un bref aperçu du sujet en se basant UNIQUEMENT sur le contenu de la question et <réponse_assistant></instruction>
            </instructions>
            
            <format_de_réponse>
                - Le titre DOIT être en FRANCAIS !
                - 4-6 mots maximum
                - Répondre uniquement avec le titre !!!
            </format_de_réponse>
            
            <exemples>
                <exemple>AVS: Calcul des rentes</exemple>
                <exemple>Age de la retraite</exemple>
                <exemple>Assurance invalidité: Conditions d'octroi</exemple>
                <exemple>Assurance chômage: Démarches administratives</exemple>
            </exemples>
            
            <réponse_assistant>
                %s
            <réponse_assistant>
            """;

    public static final String CONVERSATION_TITLE_PROMPT_IT = """
            <istruzioni>
                <istruzione>Il vostro compito è generare un titolo per la cronologia della chat a partire dalla domanda dell'utente e dalla <risposta_assistente></istruzione>
                <istruzione>Generare un titolo di alto livello dalla Domanda e dalla <risposta_assistente> che catturi l'essenza della conversazione che ne è seguita</istruzione>
                <istruzione>Il titolo deve essere estremamente conciso e informativo (MASSIMO 4-6 PAROLE), fornendo una breve panoramica dell'argomento basata SOLO sul contenuto della Domanda e della <risposta_assistente></istruzione>
            </istruzioni>
            
            <formato_risposta>
                - Il titolo DEVE essere in italiano!
                - 4-6 parole al massimo
                - Rispondere solo con il titolo !!!
            </formato_risposta>
            
            <esempi>
                <esempio>AVS: Calcolo delle pensioni</esempio>
                <esempio>Età pensionabile</esempio>
                <esempio>Assicurazione d'invalidità: Condizioni di diritto</esempio>
                <esempio>Assicurazione contro la disoccupazione: Procedure amministrative</esempio>
            <esempi>
            
            <risposta_assistente>
                %s
            </risposta_assistente>
            """;

    public static final String CONVERSATION_TITLE_PROMPT_DE = """
            <instruktionen>
                <instruktion>Ihre Aufgabe ist es, einen Titel für den Chatverlauf aus der Frage des Nutzers und der <assistent_antwort> zu generieren</instruktion>
                <instruktion>Generieren Sie aus Frage und <assistent_antwort> einen hochrangigen Titel, der die Essenz des anschliessenden Gesprächs einfängt</instruktion>
                <instruktion>Die Überschrift sollte äusserst prägnant und informativ sein (MAXIMAL 4-6 WÖRTER) und einen kurzen Überblick über das Thema geben, der NUR auf dem Inhalt von Frage und <assistent_antwort> beruht</instruktion>
            </instruktionen>
            
            <antwort_format>
                - Der Titel MUSS auf DEUTSCH sein!
                - Maximal 4-6 Wörter
                - Nur mit dem Titel antworten!!!
            </antwort_format>
            
            <beispiele>
                <beispiel>AHV: Berechnung der Renten</beispiel>
                <beispiel>Rentenalter</beispiel>
                <beispiel>Invalidenversicherung: Bedingungen für den Anspruch</beispiel>
                <beispiel>Arbeitslosenversicherung: Administrative Schritte</beispiel>
            </beispiele>
            
            <assistent_antwort>
                %s
            </assistent_antwort>
            """;

    private ConversationPrompts() {}

    public static String getConversationTitlePrompt(String lang) {
        return switch (lang) {
            case "fr" -> CONVERSATION_TITLE_PROMPT_FR;
            case "it" -> CONVERSATION_TITLE_PROMPT_IT;
            default -> CONVERSATION_TITLE_PROMPT_DE;
        };
    }

    public static String getTopicCheckPrompt(String lang) {
        return switch (lang) {
            case "fr" -> TOPIC_CHECK_PROMPT_FR;
            case "it" -> TOPIC_CHECK_PROMPT_IT;
            default -> TOPIC_CHECK_PROMPT_DE;
        };
    }

    public static String getOffTopicMessage(String lang) {
        return switch (lang) {
            case "fr" -> OFF_TOPIC_MESSAGE_FR;
            case "it" -> OFF_TOPIC_MESSAGE_IT;
            default -> OFF_TOPIC_MESSAGE_DE;
        };
    }
}
