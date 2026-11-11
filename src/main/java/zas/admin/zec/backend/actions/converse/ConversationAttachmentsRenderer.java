package zas.admin.zec.backend.actions.converse;

import zas.admin.zec.backend.persistence.repository.AttachmentRepository.AttachmentSummary;

import java.util.List;

/**
 * Bloc {@code <conversation_attachments>} préfixé à la question courante : fichiers attachés à la conversation
 * (nom et statut OCR, sans contenu). L'agent décide lui-même de lire un fichier via {@code get_attachment_content}.
 *
 * <p>Rendu uniquement sur la question courante, pas sur l'historique : la liste et les statuts évoluent d'un tour
 * à l'autre (upload, fin d'OCR, suppression) et aucune date d'upload ne permet de rattacher un fichier à un tour
 * passé. Seul le dernier message utilisateur varie, le préfixe de l'historique reste stable (prefix caching).</p>
 */
final class ConversationAttachmentsRenderer {

    private ConversationAttachmentsRenderer() {}

    static String render(List<AttachmentSummary> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("<conversation_attachments>\n");
        for (AttachmentSummary attachment : attachments) {
            sb.append("- filename=\"").append(clean(attachment.getFilename())).append("\"; status=")
                    .append(status(attachment.getStatus())).append('\n');
        }
        return sb.append("</conversation_attachments>").toString();
    }

    private static String status(AttachmentStatus status) {
        return switch (status) {
            case PROCESSED -> "ready";
            case PENDING -> "processing";
            case FAILED -> "failed";
            case null -> "unknown";
        };
    }

    private static String clean(String filename) {
        return filename == null ? "" : filename.replace('"', '\'').replace('\n', ' ').strip();
    }
}
