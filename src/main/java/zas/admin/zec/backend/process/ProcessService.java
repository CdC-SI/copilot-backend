package zas.admin.zec.backend.process;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.persistence.entity.BusinessProcessEntity;
import zas.admin.zec.backend.persistence.repository.BusinessProcessRepository;
import zas.admin.zec.backend.persistence.repository.BusinessProcessRepository.ProcessNameView;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Source unique des processus métier BPMN : chargement depuis {@code business_process}, recherche
 * par identifiant ou par nom, résolution des sous-processus et mise en forme pour le contexte du LLM.
 *
 * <p>Utilisé par l'enchaînement automatique de {@code RAGTool} (documents retrouvés -> processus
 * cités), par le tool {@code get_business_process} (demande explicite) et par {@code RAGChatService}
 * (réinjection du processus d'un tour à l'autre).</p>
 */
@Slf4j
@Service
public class ProcessService {

    /** Profondeur des callActivity suivies : 2 mesurée sur le corpus actuel, 3 laisse une marge. */
    static final int MAX_CALL_DEPTH = 3;
    private static final int MAX_CANDIDATES = 5;
    private static final double MIN_CANDIDATE_SCORE = 0.5;
    private static final Set<String> STOP_WORDS = Set.of(
            "le", "la", "les", "un", "une", "des", "du", "de", "d", "l", "et", "ou", "a", "au", "aux",
            "en", "pour", "par", "sur", "processus", "process");

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final BusinessProcessRepository repository;

    public ProcessService(BusinessProcessRepository repository) {
        this.repository = repository;
    }

    /**
     * Résultat d'une recherche par nom : un processus unique, ou des candidats à proposer à
     * l'utilisateur quand le nom est ambigu, ou rien.
     */
    public record NameMatch(BusinessProcess process, List<String> candidates) {
        static NameMatch of(BusinessProcess process) {
            return new NameMatch(process, List.of());
        }

        static NameMatch ambiguous(List<String> candidates) {
            return new NameMatch(null, candidates);
        }

        static NameMatch none() {
            return new NameMatch(null, List.of());
        }

        public boolean found() {
            return process != null;
        }
    }

    public List<BusinessProcess> findByBpmnIds(Collection<String> bpmnIds) {
        if (bpmnIds.isEmpty()) {
            return List.of();
        }
        return inRequestedOrder(bpmnIds, repository.findByBpmnIdIn(bpmnIds), BusinessProcess::bpmnId);
    }

    public List<BusinessProcess> findByBpandaIds(Collection<String> bpandaIds) {
        if (bpandaIds.isEmpty()) {
            return List.of();
        }
        return inRequestedOrder(bpandaIds, repository.findByBpandaIdIn(bpandaIds), BusinessProcess::bpandaId);
    }

    /**
     * Processus cités dans les {@code outgoing_links} des documents, dans l'ordre des documents
     * (donc du classement du retrieval), sans doublon.
     */
    public List<BusinessProcess> findReferencedBy(Collection<Document> documents) {
        Set<String> bpandaIds = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (Document document : documents) {
            var refs = ProcessLinkExtractor.extract(document.getMetadata());
            bpandaIds.addAll(refs.bpandaIds());
            names.addAll(refs.names());
        }

        Map<String, BusinessProcess> found = new LinkedHashMap<>();
        findByBpandaIds(bpandaIds).forEach(p -> found.putIfAbsent(p.bpmnId(), p));
        for (String name : names) {
            NameMatch match = findByName(name);
            if (match.found()) {
                found.putIfAbsent(match.process().bpmnId(), match.process());
            } else {
                log.warn("Lien de processus '{}' sans correspondance unique dans business_process", name);
            }
        }
        return List.copyOf(found.values());
    }

    /**
     * Recherche tolérante (accents, casse, ponctuation, pluriels) : nom exact, puis inclusion d'un
     * nom dans l'autre, puis recouvrement des mots significatifs. Un seul processus qui couvre tous
     * les mots de la requête est retenu ; sinon les meilleurs candidats sont rendus.
     */
    public NameMatch findByName(String name) {
        if (name == null || name.isBlank()) {
            return NameMatch.none();
        }
        String query = normalize(name);
        List<ProcessNameView> all = repository.findAllNames();

        List<ProcessNameView> exact = all.stream()
                .filter(p -> normalize(p.getProcessName()).equals(query))
                .toList();
        if (!exact.isEmpty()) {
            return resolve(exact);
        }

        List<ProcessNameView> contained = all.stream()
                .filter(p -> {
                    String candidate = normalize(p.getProcessName());
                    return candidate.contains(query) || query.contains(candidate);
                })
                .toList();
        if (!contained.isEmpty()) {
            return resolve(contained);
        }

        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) {
            return NameMatch.none();
        }
        record Scored(ProcessNameView view, double score) {}
        List<Scored> scored = all.stream()
                .map(p -> new Scored(p, overlap(queryTokens, tokens(normalize(p.getProcessName())))))
                .filter(s -> s.score() >= MIN_CANDIDATE_SCORE)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .toList();
        if (scored.isEmpty()) {
            return NameMatch.none();
        }
        boolean uniqueFullMatch = scored.getFirst().score() == 1.0
                && (scored.size() == 1 || scored.get(1).score() < 1.0);
        if (uniqueFullMatch) {
            return resolve(List.of(scored.getFirst().view()));
        }
        return NameMatch.ambiguous(scored.stream()
                .limit(MAX_CANDIDATES)
                .map(s -> s.view().getProcessName())
                .toList());
    }

    /**
     * Suit les callActivity de proche en proche (parcours en largeur) et rend les sous-processus
     * atteints dans l'ordre de découverte, sans le processus de départ ni doublon (protection
     * anti-cycle, même si le corpus actuel n'en contient pas).
     */
    public List<BusinessProcess> resolveCalledProcesses(BusinessProcess process) {
        Set<String> visited = new HashSet<>(Set.of(process.bpmnId()));
        List<BusinessProcess> found = new ArrayList<>();
        List<BusinessProcess> frontier = List.of(process);
        for (int depth = 0; depth < MAX_CALL_DEPTH && !frontier.isEmpty(); depth++) {
            Set<String> next = new LinkedHashSet<>();
            for (BusinessProcess parent : frontier) {
                for (String called : parent.calledBpmnIds()) {
                    if (visited.add(called)) {
                        next.add(called);
                    }
                }
            }
            frontier = findByBpmnIds(next);
            found.addAll(frontier);
        }
        return found;
    }

    /**
     * Bloc injecté dans le contexte du LLM : le JSON du processus, puis celui de chaque
     * sous-processus appelé. Même contenu que le format validé dans les tests ZIA_622.
     */
    public String render(BusinessProcess process) {
        StringBuilder block = new StringBuilder()
                .append("<processus nom=\"").append(attribute(process.name()))
                .append("\" etat=\"").append(attribute(process.state())).append("\">")
                .append(System.lineSeparator()).append(process.json()).append(System.lineSeparator())
                .append("</processus>");
        for (BusinessProcess sub : resolveCalledProcesses(process)) {
            block.append(System.lineSeparator())
                    .append("<sous_processus nom=\"").append(attribute(sub.name()))
                    .append("\" appele_par=\"").append(attribute(process.name())).append("\">")
                    .append(System.lineSeparator()).append(sub.json()).append(System.lineSeparator())
                    .append("</sous_processus>");
        }
        return block.toString();
    }

    /** Liste des noms exacts des processus, une ligne par processus, pour le system prompt. */
    public String catalog() {
        return repository.findAllNames().stream()
                .map(ProcessNameView::getProcessName)
                .filter(Objects::nonNull)
                .map(name -> "- " + name)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private NameMatch resolve(List<ProcessNameView> matches) {
        if (matches.size() > 1) {
            return NameMatch.ambiguous(matches.stream()
                    .limit(MAX_CANDIDATES)
                    .map(ProcessNameView::getProcessName)
                    .toList());
        }
        return findByBpmnIds(List.of(matches.getFirst().getBpmnId())).stream()
                .findFirst()
                .map(NameMatch::of)
                .orElseGet(NameMatch::none);
    }

    private List<BusinessProcess> inRequestedOrder(Collection<String> keys, List<BusinessProcessEntity> entities,
                                                   Function<BusinessProcess, String> keyOf) {
        Map<String, BusinessProcess> byKey = new HashMap<>();
        for (BusinessProcessEntity entity : entities) {
            BusinessProcess process = toBusinessProcess(entity);
            if (process != null && keyOf.apply(process) != null) {
                byKey.putIfAbsent(keyOf.apply(process), process);
            }
        }
        return keys.stream().distinct().map(byKey::get).filter(Objects::nonNull).toList();
    }

    static BusinessProcess toBusinessProcess(BusinessProcessEntity entity) {
        try {
            JsonNode root = MAPPER.readTree(entity.getContent());
            List<String> called = new ArrayList<>();
            for (JsonNode node : root.path("nodes")) {
                String calledId = text(node, "called_bpmn_id");
                if (calledId != null && !called.contains(calledId)) {
                    called.add(calledId);
                }
            }
            return new BusinessProcess(
                    entity.getBpmnId(),
                    text(root, "bpanda_id"),
                    text(root, "process_name"),
                    text(root, "process_state"),
                    text(root, "bpanda_link"),
                    List.copyOf(called),
                    MAPPER.writeValueAsString(root));
        } catch (JacksonException e) {
            log.error("JSON invalide pour le processus {}", entity.getBpmnId(), e);
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private static String attribute(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .strip();
    }

    /** Mots significatifs, au singulier approximatif (« demandes » et « demande » se rejoignent). */
    private static Set<String> tokens(String normalized) {
        return Arrays.stream(normalized.split(" "))
                .filter(t -> !t.isBlank() && !STOP_WORDS.contains(t))
                .map(t -> t.length() > 3 && t.endsWith("s") ? t.substring(0, t.length() - 1) : t)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static double overlap(Set<String> queryTokens, Set<String> candidateTokens) {
        long shared = queryTokens.stream().filter(candidateTokens::contains).count();
        return (double) shared / queryTokens.size();
    }
}
