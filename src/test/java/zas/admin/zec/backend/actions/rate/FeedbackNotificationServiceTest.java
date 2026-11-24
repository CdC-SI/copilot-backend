package zas.admin.zec.backend.actions.rate;

import freemarker.template.Configuration;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;
import zas.admin.zec.backend.actions.analyze.FeedbackDTO;
import zas.admin.zec.backend.actions.analyze.FeedbackReportService.FeedbackReport;
import zas.admin.zec.backend.actions.analyze.FeedbackReportService.PendingItem;
import zas.admin.zec.backend.config.properties.FeedbackMailProperties;
import zas.admin.zec.backend.persistence.entity.FeedbackCategory;
import zas.admin.zec.backend.persistence.entity.MessageFeedbackEntity;
import zas.admin.zec.backend.persistence.entity.SourceFeedbackEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedbackNotificationServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private Configuration freemarker;

    @BeforeEach
    void setUp() throws Exception {
        // Configuration FreeMarker construite comme en production (spring.freemarker.* de application.yml).
        var factory = new FreeMarkerConfigurationFactoryBean();
        factory.setTemplateLoaderPath("classpath:/templates/");
        factory.setPreferFileSystemAccess(false);
        factory.setDefaultEncoding("UTF-8");
        factory.afterPropertiesSet();
        freemarker = factory.getObject();
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void notifyNegativeFeedback_sourceFeedback_rendersTemplateAndSendsToModerators() throws Exception {
        var feedback = new SourceFeedbackEntity();
        feedback.setUserUuid("user-1");
        feedback.setConversationUuid("conv-1");
        feedback.setMessageUuid("msg-1");
        feedback.setDocumentId("doc-42");
        feedback.setComment("La source <b>est fausse</b>");
        feedback.setCategory(FeedbackCategory.WRONG_SOURCE);
        feedback.setTimestamp(LocalDateTime.of(2026, 3, 1, 10, 0));

        service(properties(true, List.of("mod@zas.ch"))).notifyNegativeFeedback(feedback);

        var message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("[ZIA] Nouveau feedback négatif");
        assertThat(message.getFrom()[0].toString()).isEqualTo("noreply@zas.ch");
        assertThat(message.getRecipients(Message.RecipientType.TO)).extracting(Object::toString).containsExactly("mod@zas.ch");
        var body = message.getContent().toString();
        assertThat(body)
                .contains("Source", "WRONG_SOURCE", "doc-42", "conv-1", "https://zia.example")
                // Le gabarit est en sortie HTML : le commentaire de l'utilisateur est échappé.
                .contains("La source &lt;b&gt;est fausse&lt;/b&gt;")
                .doesNotContain("<b>est fausse</b>");
    }

    @Test
    void notifyNegativeFeedback_messageFeedbackWithoutOptionalFields_usesPlaceholders() throws Exception {
        var feedback = new MessageFeedbackEntity();
        feedback.setUserUuid("user-1");
        feedback.setConversationUuid("conv-1");
        feedback.setMessageUuid("msg-1");
        feedback.setScore(-1);
        feedback.setTimestamp(LocalDateTime.of(2026, 3, 1, 10, 0));

        service(properties(true, List.of("mod@zas.ch"))).notifyNegativeFeedback(feedback);

        var body = sentMessage().getContent().toString();
        assertThat(body).contains("Message", "non renseignée").doesNotContain("Document");
    }

    @Test
    void notifyNegativeFeedback_disabledOrWithoutRecipients_sendsNothing() {
        service(properties(false, List.of("mod@zas.ch"))).notifyNegativeFeedback(new MessageFeedbackEntity());
        service(properties(true, List.of())).notifyNegativeFeedback(new MessageFeedbackEntity());
        service(properties(true, null)).notifyNegativeFeedback(new MessageFeedbackEntity());

        verifyNoInteractions(mailSender);
    }

    @Test
    void notifyNegativeFeedback_mailFailure_isSwallowed() {
        doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(MimeMessage.class));
        var feedback = new MessageFeedbackEntity();
        feedback.setUserUuid("u");
        feedback.setConversationUuid("c");
        feedback.setMessageUuid("m");
        feedback.setTimestamp(LocalDateTime.now());

        assertThatCode(() -> service(properties(true, List.of("mod@zas.ch"))).notifyNegativeFeedback(feedback))
                .doesNotThrowAnyException();
    }

    @Test
    void sendWeeklyReport_rendersCountsCategoriesAndPendingItemsToGivenRecipients() throws Exception {
        var report = new FeedbackReport(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 8, 0, 0),
                8, 3, 1, 4,
                List.of(new FeedbackDTO.ByCategory(FeedbackCategory.WRONG_ANSWER, 4)),
                List.of(new PendingItem(Instant.parse("2026-03-02T10:00:00Z"), "Message", null, "Combien de rente ?")));

        service(properties(true, List.of("mod@zas.ch"))).sendWeeklyReport(report, List.of("chef@zas.ch", "adj@zas.ch"));

        var message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("[ZIA] Rapport hebdomadaire des feedbacks");
        assertThat(message.getRecipients(Message.RecipientType.TO)).extracting(Object::toString)
                .containsExactly("chef@zas.ch", "adj@zas.ch");
        assertThat(message.getContent().toString())
                .contains("2026-03-01T00:00", "2026-03-08T00:00")
                .contains("<td>8</td>", "<td>3</td>", "<td>1</td>", "<td>4</td>")
                .contains("<tr><td>WRONG_ANSWER</td><td>4</td></tr>")
                .contains("2026-03-02T10:00:00Z", "<td>Message</td>", "<td>—</td>", "<td>Combien de rente ?</td>")
                .contains("https://zia.example")
                .doesNotContain("Aucun feedback en attente");
    }

    @Test
    void sendWeeklyReport_withoutPendingItems_saysSo() throws Exception {
        var report = new FeedbackReport(LocalDateTime.now(), LocalDateTime.now(), 0, 0, 0, 0, List.of(), List.of());

        service(properties(true, List.of())).sendWeeklyReport(report, List.of("chef@zas.ch"));

        assertThat(sentMessage().getContent().toString()).contains("Aucun feedback en attente");
    }

    @Test
    void sendWeeklyReport_disabledOrWithoutRecipients_sendsNothing() {
        var report = new FeedbackReport(LocalDateTime.now(), LocalDateTime.now(), 0, 0, 0, 0, List.of(), List.of());

        service(properties(false, List.of())).sendWeeklyReport(report, List.of("chef@zas.ch"));
        service(properties(true, List.of())).sendWeeklyReport(report, List.of());
        service(properties(true, List.of())).sendWeeklyReport(report, null);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendWeeklyReport_templateFailure_isSwallowed() {
        var broken = new FeedbackNotificationService(mailSender, new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS),
                properties(true, List.of()));
        var report = new FeedbackReport(LocalDateTime.now(), LocalDateTime.now(), 0, 0, 0, 0, List.of(), List.of());

        assertThatCode(() -> broken.sendWeeklyReport(report, List.of("chef@zas.ch"))).doesNotThrowAnyException();
        verify(mailSender, org.mockito.Mockito.never()).send(any(MimeMessage.class));
    }

    private FeedbackNotificationService service(FeedbackMailProperties properties) {
        return new FeedbackNotificationService(mailSender, freemarker, properties);
    }

    private MimeMessage sentMessage() {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static FeedbackMailProperties properties(boolean enabled, List<String> recipients) {
        return new FeedbackMailProperties(enabled, "noreply@zas.ch", recipients, "[ZIA]", "https://zia.example", true);
    }
}
