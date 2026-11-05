package zas.admin.zec.backend.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prompt système agentique ({@link RAGPrompts#getAgenticSystemPrompt}) : contrat commun aux trois langues (ce que le
 * code injecte ou retire), et règles de la version {@value RAGPrompts#AGENTIC_PROMPT_VERSION}.
 */
class RAGPromptsTest {

    private static final List<String> SYSTEM_BLOCKS = List.of("<retrieved_sources", "<conversation_attachments>",
            "<linked_sources>", "<linked_processes>");
    /** Balises que le code ou les outils insèrent dans la conversation. */
    private static final List<String> INJECTED_TAGS = List.of("retrieved_sources", "conversation_attachments", "linked_sources",
            "linked_processes", "linked_process", "document", "attachment", "no_documentation_found",
            "no_document_found", "tool_call_limit_reached");
    private static final List<String> TOOLS = List.of("search_social_insurance_documentation", "get_documents",
            "get_attachment_content");

    @ParameterizedTest
    @CsvSource({"fr", "de", "it"})
    void everyLanguageHonoursTheCodeContract(String lang) {
        String prompt = RAGPrompts.getAgenticSystemPrompt(lang);

        assertTrue(prompt.contains("<turn_meta status="), "balise de fin de tour déclarée");
        assertEquals(1, prompt.split("%", -1).length - 1, "un seul %, celui du format de réponse");
        assertTrue(prompt.formatted("Markdown").contains("Markdown"), "le prompt reste formatable");
        SYSTEM_BLOCKS.forEach(block -> assertTrue(prompt.contains(block), "bloc système déclaré : " + block));
        // La consigne de domaine choisi porte un nom propre à chaque langue : celui que le code injecte.
        String directive = RAGPrompts.getForcedRetrievalDirective(lang, "X").strip();
        String directiveTag = directive.substring(0, directive.indexOf('>') + 1);
        assertTrue(prompt.contains(directiveTag), "bloc système déclaré : " + directiveTag);
        TOOLS.forEach(tool -> assertTrue(prompt.contains(tool), "outil nommé : " + tool));

        // Toute balise citée (<methode>, <blocs>…) est une section du prompt ou un bloc injecté par le code : une
        // traduction ne doit pas garder le nom français d'une section ou de la consigne de domaine.
        Set<String> known = new HashSet<>(INJECTED_TAGS);
        known.add(directiveTag.substring(1, directiveTag.length() - 1));
        Matcher section = Pattern.compile("(?m)^<([^/>\\s]+)>$").matcher(prompt);
        while (section.find()) {
            known.add(section.group(1));
        }
        Matcher reference = Pattern.compile("<([\\p{L}_]+)>").matcher(prompt);
        while (reference.find()) {
            assertTrue(known.contains(reference.group(1)), lang + " : balise citée inconnue <" + reference.group(1) + ">");
        }
    }

    @Test
    void unknownLanguageFallsBackToGerman() {
        assertEquals(RAGPrompts.getAgenticSystemPrompt("de"), RAGPrompts.getAgenticSystemPrompt("en"));
        assertEquals(RAGPrompts.getAgenticSystemPrompt("de"), RAGPrompts.getAgenticSystemPrompt(null));
    }

    /**
     * Contradictions de l'ancien prompt tranchées : des sources qui se contredisent mènent à la méthode (signaler,
     * demander) et non à un refus pour documentation insuffisante (C1) ; plus de consigne « soyez décisif » (C4) ni de
     * retenue sur les questions (C7) ; un seul critère answer / followup (C3).
     */
    @Test
    void promptResolvesTheContradictionsOfThePreviousVersion() {
        String v1 = RAGPrompts.getAgenticSystemPrompt("fr");

        assertFalse(v1.contains("décisif"), "C4");
        assertFalse(v1.contains("sans en abuser"), "C7");
        String sources = section(v1, "sources");
        assertTrue(sources.contains("se contredisent") && sources.contains("appliquez <methode>"), "C1");
        String method = section(v1, "methode");
        assertTrue(method.contains("n'est jamais supposé") && method.contains("ne concluez pas")
                && method.contains("une seule question"), "méthode : ne jamais supposer, demander");
        assertTrue(method.contains("Le silence d'une source n'est pas un écart")
                && !method.contains("condition présente dans une seule source"),
                "variante A : une condition énoncée par une seule source s'applique, seul un désaccord fait demander");
        assertFalse(method.contains("status="), "C3 : le statut n'est défini que dans <fin_de_tour>");
        assertTrue(section(v1, "fin_de_tour").contains("même s'il se termine par une question"), "C3");
        assertTrue(v1.length() < 13_000, "prompt compact : " + v1.length() + " caractères (ancien : 25 248)");
    }

    /**
     * Libellés propres à chaque langue : sections du prompt (réponse, méthode, processus), structure des réponses à un
     * cas concret (issue en titre, tableau des conditions aux résultats fermés, point à préciser, question en dernier)
     * et règle de la variante A (le silence d'une source n'est pas un écart).
     */
    static Stream<Arguments> languages() {
        return Stream.of(
                Arguments.of("fr", "reponse", "methode", "processus", List.of("titre de niveau 2", "« À compléter : … »",
                        "### Conditions vérifiées", "« ✅ Remplie », « ⛔ Non remplie », « ❓ À vérifier »",
                        "« ⚠️ Écart entre les sources »", "### Point à préciser", "### Suite du traitement",
                        "« **Question :** »", "mais jamais sur <methode>"),
                        "Le silence d'une source n'est pas un écart"),
                Arguments.of("de", "antwort", "methode", "prozess", List.of("Überschrift der Stufe 2", "« Zu ergänzen: … »",
                        "### Geprüfte Bedingungen", "« ✅ Erfüllt », « ⛔ Nicht erfüllt », « ❓ Zu prüfen »",
                        "« ⚠️ Abweichung zwischen den Quellen »", "### Zu präzisierender Punkt",
                        "### Fortsetzung der Bearbeitung", "« **Frage:** »", "niemals aber vor <methode>"),
                        "Das Schweigen einer Quelle ist keine Abweichung"),
                Arguments.of("it", "risposta", "metodo", "processo", List.of("titolo di livello 2", "« Da completare: … »",
                        "### Condizioni verificate", "« ✅ Soddisfatta », « ⛔ Non soddisfatta », « ❓ Da verificare »",
                        "« ⚠️ Divergenza tra le fonti »", "### Punto da precisare", "### Seguito del trattamento",
                        "« **Domanda:** »", "ma mai su <metodo>"),
                        "Il silenzio di una fonte non è una divergenza"));
    }

    @ParameterizedTest
    @MethodSource("languages")
    void imposesAStructureForCaseAnswers(String lang, String answerTag, String methodTag, String processTag,
                                         List<String> markers, String silenceRule) {
        String prompt = RAGPrompts.getAgenticSystemPrompt(lang);
        String answer = section(prompt, answerTag);

        markers.forEach(marker -> assertTrue(answer.contains(marker), lang + " structure : " + marker));
        assertTrue(section(prompt, methodTag).contains(silenceRule), lang + " variante A");
    }

    /**
     * Le prompt décrit le format « next » du preprocessing ({@code processes convert-graph --graph-format next}) :
     * sorties portées par chaque élément, décisions et boucles marquées.
     */
    @ParameterizedTest
    @MethodSource("languages")
    void describesTheNextGraphFormat(String lang, String answerTag, String methodTag, String processTag,
                                     List<String> markers, String silenceRule) {
        String process = section(RAGPrompts.getAgenticSystemPrompt(lang), processTag);

        for (String key : List.of("nodes", "(next)", "\"if\"", "default_role", "\"decision\": true",
                "\"loop\": true")) {
            assertTrue(process.contains(key), lang + " format next : " + key);
        }
        assertFalse(process.contains("arcs") || process.contains("kind_raw"), lang + " plus de format arcs");
    }

    private static String section(String prompt, String tag) {
        int start = prompt.indexOf("<" + tag + ">");
        int end = prompt.indexOf("</" + tag + ">");
        assertTrue(start >= 0 && end > start, "section <" + tag + ">");
        return prompt.substring(start, end);
    }
}
