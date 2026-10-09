package zas.admin.zec.backend.process;

import java.util.List;

/**
 * Processus métier BPMN chargé depuis {@code business_process}.
 *
 * @param bpmnId        identifiant du processus dans le XML BPMN
 * @param bpandaId      identifiant BPanda (paramètre {@code processId} des liens des documents), peut être nul
 * @param name          nom exact du processus
 * @param state         état de publication (ex. « Brouillon »), peut être nul
 * @param bpandaLink    lien vers la page BPanda du processus, peut être nul
 * @param calledBpmnIds processus appelés par les callActivity, dans l'ordre des nœuds
 * @param json          JSON complet du processus, compact, tel qu'injecté dans le contexte du LLM
 */
public record BusinessProcess(
        String bpmnId,
        String bpandaId,
        String name,
        String state,
        String bpandaLink,
        List<String> calledBpmnIds,
        String json) {
}
