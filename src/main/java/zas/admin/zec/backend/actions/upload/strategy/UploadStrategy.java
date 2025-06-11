package zas.admin.zec.backend.actions.upload.strategy;

import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;

public sealed interface UploadStrategy permits EmbeddedDocUploadStrategy, SourceDocUploadStrategy {
    /**
     * Uploads the document to the according table.
     */
    void upload(DocumentToUpload file);
}
