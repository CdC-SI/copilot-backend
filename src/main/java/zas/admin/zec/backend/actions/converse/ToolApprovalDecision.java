package zas.admin.zec.backend.actions.converse;

/** T1 : corps de la décision sur un appel de tool soumis à approbation. */
public record ToolApprovalDecision(Decision decision) {

    public enum Decision { APPROVE, REJECT }
}
