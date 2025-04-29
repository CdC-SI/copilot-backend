package zas.admin.zec.backend.rag;

public record Document(
        String text,
        String url,
        String[] tags,
        String summary,
        String[] subtopics,
        String[] organizations,
        String language,
        String[] hyqDeclarative,
        String[] hyq,
        String doctype) {
}
