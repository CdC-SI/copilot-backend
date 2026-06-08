package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.converse.AttachmentStatus;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.rag.ChatStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tools Spring AI exposant les pièces jointes attachées à la conversation courante.
 *
 * <p>Deux tools sont fournis :
 * <ul>
 *   <li>{@code list_conversation_attachments} – retourne les noms des fichiers attachés, utile
 *       pour que le LLM détermine quel fichier l'utilisateur mentionne.</li>
 *   <li>{@code get_attachment_content} – retourne le contenu textuel (OCR) d'un fichier attaché,
 *       utilisable pour la traduction, le résumé, l'extraction d'informations, etc.</li>
 * </ul>
 *
 * <p>Le {@code conversationId} et l'{@code userId} sont transmis via le {@link ToolContext}
 * (clés définies dans {@link ToolContextKeys}) ; ils ne sont jamais fournis par le LLM.</p>
 */
@Slf4j
@Component
public class ConversationAttachmentTool {

    private final AttachmentRepository attachmentRepository;

    public ConversationAttachmentTool(AttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    @Tool(name = "list_conversation_attachments", description = """
            Retourne la liste des noms de fichiers attachés à la conversation en cours. \
            À utiliser pour identifier quel fichier l'utilisateur mentionne lorsqu'il fait \
            référence à une pièce jointe sans préciser le nom exact, afin de déterminer \
            quel fichier cibler avant d'appeler get_attachment_content.""")
    public String listConversationAttachments(ToolContext toolContext) {
        var context = contextMap(toolContext);
        var conversationId = asString(context.get(ToolContextKeys.CTX_CONVERSATION_ID));
        var userId = asString(context.get(ToolContextKeys.CTX_USER_ID));
        var language = asString(context.get(ToolContextKeys.CTX_LANGUAGE));

        // Notifier le frontend qu’on liste les pièces jointes
        ToolContextKeys.emitStatus(context, ChatStatus.ATTACHMENT, language.isBlank() ? "fr" : language);

        if (conversationId.isBlank() || userId.isBlank()) {
            return "Aucune pièce jointe disponible (contexte de conversation manquant).";
        }

        var attachments = attachmentRepository.findAllByConversationIdAndUserId(conversationId, userId);
        if (attachments.isEmpty()) {
            return "Aucune pièce jointe n'est attachée à cette conversation.";
        }

        log.debug("Listing {} attachment(s) for conversation '{}'", attachments.size(), conversationId);
        return attachments.stream()
                .map(a -> "%s [%s]".formatted(a.getFilename(), a.getStatus().getDescription()))
                .collect(Collectors.joining("\n- ", "Fichiers attachés à cette conversation :\n- ", ""));
    }

    @Tool(name = "get_attachment_content", description = """
            Retourne le contenu textuel (extrait par OCR) d'un fichier attaché à la conversation. \
            À utiliser dès que l'utilisateur demande une action sur une pièce jointe : traduction, \
            résumé, extraction d'informations, reformulation, etc. \
            Si un seul fichier est attaché, invoquer ce tool directement sans appeler \
            list_conversation_attachments au préalable. \
            Si plusieurs fichiers sont présents et que le nom n'est pas précisé, appeler d'abord \
            list_conversation_attachments pour déterminer quel fichier est concerné.""")
    public String getAttachmentContent(
            @ToolParam(description = """
                    Nom du fichier dont récupérer le contenu, tel qu'il a été uploadé \
                    (ex. : « rapport.pdf »). Peut être null ou vide si un seul fichier \
                    est attaché à la conversation.""", required = false)
            String filename,
            ToolContext toolContext) {

        var context = contextMap(toolContext);
        var conversationId = asString(context.get(ToolContextKeys.CTX_CONVERSATION_ID));
        var userId = asString(context.get(ToolContextKeys.CTX_USER_ID));
        var language = asString(context.get(ToolContextKeys.CTX_LANGUAGE));

        // Notifier le frontend que le parsing de la pièce jointe est en cours.
        ToolContextKeys.emitStatus(context, ChatStatus.OCR, language.isBlank() ? "fr" : language);

        if (conversationId.isBlank() || userId.isBlank()) {
            return "Impossible de récupérer la pièce jointe : contexte de conversation manquant.";
        }

        var attachments = attachmentRepository.findAllByConversationIdAndUserId(conversationId, userId);

        if (attachments.isEmpty()) {
            return "Aucune pièce jointe n'est attachée à cette conversation.";
        }

        // Un seul fichier et pas de nom fourni → retourner directement
        if (attachments.size() == 1 && (filename == null || filename.isBlank())) {
            var entity = attachments.getFirst();
            log.debug("Single attachment '{}' returned for conversation '{}'", entity.getFilename(), conversationId);
            return formatContent(entity);
        }

        // Plusieurs fichiers avec un nom fourni → matching insensible à la casse, partiel
        if (filename != null && !filename.isBlank()) {
            var normalized = filename.strip().toLowerCase();
            List<AttachmentEntity> matches = attachments.stream()
                    .filter(a -> a.getFilename() != null && a.getFilename().toLowerCase().contains(normalized))
                    .toList();

            if (matches.size() == 1) {
                log.debug("Matched attachment '{}' for filename hint '{}' in conversation '{}'",
                        matches.getFirst().getFilename(), filename, conversationId);
                return formatContent(matches.getFirst());
            }
            if (matches.size() > 1) {
                return ("Plusieurs fichiers correspondent à « %s » : %s. " +
                        "Précisez le nom exact et appelez à nouveau ce tool.")
                        .formatted(filename, matches.stream()
                                .map(AttachmentEntity::getFilename)
                                .collect(Collectors.joining(", ")));
            }
            return "Aucun fichier nommé « %s » n'est attaché à cette conversation.".formatted(filename);
        }

        // Plusieurs fichiers, pas de nom fourni → demander une précision
        return ("Plusieurs fichiers sont attachés à cette conversation : "
                + attachments.stream().map(AttachmentEntity::getFilename).collect(Collectors.joining(", "))
                + ". Utilisez list_conversation_attachments pour identifier le bon fichier, "
                + "puis appelez à nouveau get_attachment_content avec le nom exact.");
    }

    private static String formatContent(AttachmentEntity entity) {
        if (entity.getStatus() == AttachmentStatus.PENDING) {
            return "Le fichier « %s » est encore en cours de traitement OCR. Veuillez patienter et réessayer dans quelques instants."
                    .formatted(entity.getFilename());
        }
        if (entity.getStatus() == AttachmentStatus.FAILED) {
            return "Le traitement OCR du fichier « %s » a échoué. Le contenu n'est pas disponible."
                    .formatted(entity.getFilename());
        }
        return "<attachment filename=\"%s\">%n%s%n</attachment>".formatted(
                entity.getFilename(), entity.getContent());
    }

    private static Map<String, Object> contextMap(ToolContext toolContext) {
        return toolContext != null ? toolContext.getContext() : Map.of();
    }

    private static String asString(Object value) {
        return value instanceof String s ? s.strip() : "";
    }
}
