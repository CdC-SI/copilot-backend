package zas.admin.zec.backend.rag;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ChatStatus {

    RETRIEVAL("Recherche des documents pertinents", "Suche nach relevanten Dokumenten", "Ricerca di documenti rilevanti", "retrieval"),
    ATTACHMENT("Récupération des pièces jointes", "Abrufen der Anhänge", "Recupero degli allegati", "attachments"),
    OCR("Parsing des pièces jointes", "Parsing von Anhängen", "Parsing degli allegati", "ocr"),
    II_TARIFFS("Récupération des informations des tarifs : %s", "Abrufen von Preisinformationen", "Recupero delle informazioni sui prezzi", "ii_tariffs"),
    II_TARIFFS_ANSWER("Génération de la réponse", "Antwortgenerierung", "Generazione della risposta", "ii_tariffs_answer"),
    ROUTING("Routage vers le service approprié", "Weiterleitung an den entsprechenden Dienst", "Instradamento al servizio appropriato", "routing"),
    INTENT_PROCESSING("Traitement de la demande", "Verarbeitung der Anfrage", "Elaborazione della richiesta", "intent_processing"),
    SOURCE_PROCESSING("Sélection des sources", "Auswahl der Quellen", "Selezione della fonte", "source_processing"),
    TAGS_PROCESSING("Sélection des tags", "Auswahl der Tags", "Selezione dei tag", "tag_processing"),
    AGENT_HANDOFF("%s traite votre demande", "%s bearbeitet Ihre Anfrage", "%s sta elaborando la sua richiesta", "agent_handoff"),
    TOOL_USE("Utilisation de l'outil : %s", "Verwendung des Tools: %s", "Utilizzo dello strumento: %s", "tool_use"),
    TOPIC_CHECK("Validation de la requête", "Validierungsabfrage", "Convalida della query", "topic_check");

    private final String textFR;
    private final String textDE;
    private final String textIT;
    private final String tag;

    public String text(String lang) {
        return switch (lang) {
            case "fr" -> textFR;
            case "it" -> textIT;
            default -> textDE;
        };
    }

    public String tag() {
        return tag;
    }
}
