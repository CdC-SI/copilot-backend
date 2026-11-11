package zas.admin.zec.backend.actions.upload.model;

import org.springframework.http.MediaType;

import java.util.*;

/**
 * Types de fichiers supportés pour les documents sources, identifiés par leur extension.
 * Chaque type indique la famille de conteneur attendue, utilisée pour contrôler le « magic number ».
 */
public enum SourceDocumentType {
    PDF("pdf", "application/pdf", Container.PDF),
    CSV("csv", "text/csv", Container.TEXT),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Container.OOXML),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Container.OOXML),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", Container.OOXML),
    DOC("doc", "application/msword", Container.OLE2),
    XLS("xls", "application/vnd.ms-excel", Container.OLE2),
    PPT("ppt", "application/vnd.ms-powerpoint", Container.OLE2);

    /**
     * Familles de conteneurs et types MIME détectables par Tika (tika-core, sans nom de fichier) qui leur correspondent.
     * Une liste vide signifie qu'aucun contrôle de contenu n'est effectué (fichiers texte).
     */
    public enum Container {
        PDF(Set.of("application/pdf")),
        OOXML(Set.of("application/zip", "application/x-tika-ooxml")),
        OLE2(Set.of("application/x-tika-msoffice")),
        TEXT(Set.of());

        private final Set<String> detectedMimeTypes;

        Container(Set<String> detectedMimeTypes) {
            this.detectedMimeTypes = detectedMimeTypes;
        }

        public boolean requiresContentCheck() {
            return !detectedMimeTypes.isEmpty();
        }

        public boolean matches(String detectedMimeType) {
            return detectedMimeType != null && detectedMimeTypes.contains(detectedMimeType);
        }
    }

    private final String extension;
    private final String mimeType;
    private final Container container;

    SourceDocumentType(String extension, String mimeType, Container container) {
        this.extension = extension;
        this.mimeType = mimeType;
        this.container = container;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public Container container() {
        return container;
    }

    public boolean matches(String filename) {
        return fromFilename(filename).filter(this::equals).isPresent();
    }

    public static Optional<SourceDocumentType> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        var ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.extension.equals(ext))
                .findFirst();
    }

    public static Optional<SourceDocumentType> fromMimeType(String mimeType) {
        return Arrays.stream(values())
                .filter(type -> type.mimeType.equals(mimeType))
                .findFirst();
    }

    public static MediaType mediaTypeOf(String filename) {
        return fromFilename(filename)
                .map(type -> MediaType.parseMediaType(type.mimeType))
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    public static List<String> mimeTypes(SourceDocumentType... types) {
        return Arrays.stream(types).map(SourceDocumentType::mimeType).toList();
    }
}
