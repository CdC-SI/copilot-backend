package zas.admin.zec.backend.actions.askfaq;

public record FAQItem(
        Answer answer,
        String url,
        String text,
        String[] tags,
        String summary,
        String[] subtopics,
        String[] organizations,
        String language,
        Integer id,
        String[] hyqDeclarative,
        String[] hyq,
        String doctype
) {
}
