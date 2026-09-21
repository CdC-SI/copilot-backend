package zas.admin.zec.backend.actions.rate;

import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.actions.analyze.FeedbackReportService.FeedbackReport;
import zas.admin.zec.backend.config.properties.FeedbackMailProperties;
import zas.admin.zec.backend.persistence.entity.AbstractFeedbackEntity;
import zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envoi des notifications par mail liées aux feedbacks : alerte immédiate sur feedback négatif
 * et rapport hebdomadaire. Toute erreur d'envoi est loguée sans jamais remonter à l'appelant,
 * pour ne pas impacter le flux principal (enregistrement du feedback).
 */
@Slf4j
@Service
public class FeedbackNotificationService {

    private static final String NOTIFICATION_TEMPLATE = "mail/feedback-notification.ftl";
    private static final String REPORT_TEMPLATE = "mail/feedback-report.ftl";

    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfig;
    private final FeedbackMailProperties properties;

    public FeedbackNotificationService(JavaMailSender mailSender, Configuration freemarkerConfig, FeedbackMailProperties properties) {
        this.mailSender = mailSender;
        this.freemarkerConfig = freemarkerConfig;
        this.properties = properties;
    }

    @Async("asyncExecutor")
    public void notifyNegativeFeedback(AbstractFeedbackEntity feedback) {
        if (!canSend()) {
            return;
        }

        try {
            Map<String, Object> model = new HashMap<>();
            model.put("feedbackKind", feedback instanceof SourceFeedbackEntity ? "Source" : "Message");
            model.put("category", feedback.getCategory());
            model.put("userUuid", feedback.getUserUuid());
            model.put("conversationUuid", feedback.getConversationUuid());
            model.put("messageUuid", feedback.getMessageUuid());
            model.put("comment", feedback.getComment());
            model.put("question", feedback.getQuestion());
            model.put("answer", feedback.getAnswer());
            model.put("timestamp", feedback.getTimestamp());
            model.put("appBaseUrl", properties.appBaseUrl());
            if (feedback instanceof SourceFeedbackEntity source) {
                model.put("documentId", source.getDocumentId());
            }

            String subject = properties.subjectPrefix() + " Nouveau feedback négatif";
            send(subject, NOTIFICATION_TEMPLATE, model);
        } catch (Exception e) {
            log.error("Échec de l'envoi de la notification de feedback négatif", e);
        }
    }

    @Async("asyncExecutor")
    public void sendWeeklyReport(FeedbackReport report, List<String> recipients) {
        if (!properties.enabled() || recipients == null || recipients.isEmpty()) {
            log.debug("Rapport hebdomadaire des feedbacks non envoyé (notifications désactivées ou pas de destinataire)");
            return;
        }

        try {
            Map<String, Object> model = new HashMap<>();
            model.put("periodStart", report.periodStart());
            model.put("periodEnd", report.periodEnd());
            model.put("total", report.total());
            model.put("treated", report.treated());
            model.put("obsolete", report.obsolete());
            model.put("stillNew", report.stillNew());
            model.put("byCategory", report.byCategory());
            model.put("pendingItems", report.pendingItems());
            model.put("appBaseUrl", properties.appBaseUrl());

            String subject = properties.subjectPrefix() + " Rapport hebdomadaire des feedbacks";
            send(subject, REPORT_TEMPLATE, model, recipients);
        } catch (Exception e) {
            log.error("Échec de l'envoi du rapport hebdomadaire des feedbacks", e);
        }
    }

    private boolean canSend() {
        if (!properties.enabled()) {
            log.debug("Notification de feedback désactivée (feedback.mail.enabled=false)");
            return false;
        }
        if (properties.recipients() == null || properties.recipients().isEmpty()) {
            log.debug("Aucun destinataire configuré pour la notification de feedback");
            return false;
        }
        return true;
    }

    private void send(String subject, String templateName, Map<String, Object> model) throws Exception {
        send(subject, templateName, model, properties.recipients());
    }

    private void send(String subject, String templateName, Map<String, Object> model, List<String> recipients) throws Exception {
        Template template = freemarkerConfig.getTemplate(templateName, StandardCharsets.UTF_8.name());
        StringWriter out = new StringWriter();
        template.process(model, out);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(recipients.toArray(String[]::new));
        helper.setSubject(subject);
        helper.setText(out.toString(), true);

        mailSender.send(message);
    }
}
