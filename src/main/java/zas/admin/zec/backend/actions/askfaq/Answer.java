package zas.admin.zec.backend.actions.askfaq;

public record Answer(
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
