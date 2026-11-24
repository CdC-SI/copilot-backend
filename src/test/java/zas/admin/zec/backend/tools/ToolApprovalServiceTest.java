package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import zas.admin.zec.backend.config.properties.ToolApprovalProperties;
import zas.admin.zec.backend.tools.ToolApprovalService.Decision;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cycle de vie d'une demande d'approbation (T1) : décision, expiration, annulation, interruption. */
class ToolApprovalServiceTest {

    private static ToolApprovalService service(Duration timeout, String... tools) {
        return new ToolApprovalService(new ToolApprovalProperties(List.of(tools), timeout));
    }

    @Test
    void onlyConfiguredToolsRequireApproval() {
        var approvals = service(Duration.ofSeconds(5), " get_attachment_content ", " ");

        assertTrue(approvals.requiresApproval("get_attachment_content"));
        assertFalse(approvals.requiresApproval("search_social_insurance_documentation"));
        assertFalse(service(Duration.ofSeconds(5)).requiresApproval("get_attachment_content"));
    }

    @Test
    void unknownRequestIsCancelledAndCannotBeDecided() {
        var approvals = service(Duration.ofSeconds(5));

        assertEquals(Decision.CANCELLED, approvals.await("inconnue"));
        assertFalse(approvals.decide("u1", "c1", "inconnue", true));
    }

    @Test
    void decisionTakenBeforeTheWaitIsReturnedAndTheRequestRemoved() {
        var approvals = service(Duration.ofSeconds(5));
        var request = approvals.open("u1", "c1");

        assertTrue(approvals.decide("u1", "c1", request.approvalId(), false));
        assertFalse(approvals.decide("u1", "c1", request.approvalId(), true), "premier verdict conservé");

        assertEquals(Decision.REJECTED, approvals.await(request.approvalId()));
        assertFalse(approvals.decide("u1", "c1", request.approvalId(), true), "demande retirée");
    }

    @Test
    void requestAlreadyExpiredWhenAwaitedIsExpired() {
        var approvals = service(Duration.ZERO);
        var request = approvals.open("u1", "c1");

        assertEquals(Decision.EXPIRED, approvals.await(request.approvalId()));
    }

    @Test
    void lateDecisionExpiresTheRequest() throws InterruptedException {
        var approvals = service(Duration.ofMillis(1));
        var request = approvals.open("u1", "c1");
        Thread.sleep(20);

        assertFalse(approvals.decide("u1", "c1", request.approvalId(), true));
        assertEquals(Decision.EXPIRED, approvals.await(request.approvalId()));
    }

    @Test
    void waitWithoutDecisionTimesOut() {
        var approvals = service(Duration.ofMillis(50));
        var request = approvals.open("u1", "c1");

        assertEquals(Decision.EXPIRED, approvals.await(request.approvalId()));
    }

    @Test
    void interruptedWaitIsCancelledAndKeepsTheInterruptFlag() {
        var approvals = service(Duration.ofSeconds(30));
        var request = approvals.open("u1", "c1");

        Thread.currentThread().interrupt();
        try {
            assertEquals(Decision.CANCELLED, approvals.await(request.approvalId()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void cancelAllOnlyCancelsTheRequestsOfTheConversation() {
        var approvals = service(Duration.ofSeconds(30));
        var mine = approvals.open("u1", "c1");
        var otherConversation = approvals.open("u1", "c2");
        var otherUser = approvals.open("u2", "c1");

        approvals.cancelAll("u1", "c1");

        assertEquals(Decision.CANCELLED, approvals.await(mine.approvalId()));
        assertTrue(approvals.decide("u1", "c2", otherConversation.approvalId(), true));
        assertTrue(approvals.decide("u2", "c1", otherUser.approvalId(), true));
        assertEquals(Decision.APPROVED, approvals.await(otherConversation.approvalId()));
    }
}
