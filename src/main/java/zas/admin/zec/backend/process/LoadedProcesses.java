package zas.admin.zec.backend.process;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Processus déjà présents dans le contexte du LLM pour la requête en cours (réinjectés depuis le
 * tour précédent, chargés par {@code RAGTool} ou par {@code get_business_process}). Évite d'injecter
 * deux fois le même JSON et sert à émettre les {@code ProcessToken} en fin de réponse.
 *
 * <p>Thread-safe : les tools peuvent être exécutés hors du thread de la requête.</p>
 */
public final class LoadedProcesses {

    private final Map<String, BusinessProcess> byBpmnId = new LinkedHashMap<>();

    /** @return {@code true} si le processus n'était pas encore chargé. */
    public synchronized boolean add(BusinessProcess process) {
        if (byBpmnId.containsKey(process.bpmnId())) {
            return false;
        }
        byBpmnId.put(process.bpmnId(), process);
        return true;
    }

    public synchronized boolean contains(String bpmnId) {
        return byBpmnId.containsKey(bpmnId);
    }

    /** Les {@code limit} derniers processus chargés, du plus ancien au plus récent. */
    public synchronized List<BusinessProcess> mostRecent(int limit) {
        List<BusinessProcess> all = new ArrayList<>(byBpmnId.values());
        return List.copyOf(all.subList(Math.max(0, all.size() - limit), all.size()));
    }
}
