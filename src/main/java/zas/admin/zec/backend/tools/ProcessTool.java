package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.process.BusinessProcess;
import zas.admin.zec.backend.process.LoadedProcesses;
import zas.admin.zec.backend.process.ProcessService;
import zas.admin.zec.backend.rag.ChatStatus;

import java.util.Map;

/**
 * Tool Spring AI chargeant un processus métier BPMN (étapes, décisions, acteurs) dans le contexte du
 * LLM à partir de son nom. Complète l'enchaînement automatique de {@link RAGTool} : il couvre les
 * questions explicites sur un processus dont aucun document lié n'a été retrouvé.
 *
 * <p>Le nom fourni par le LLM est rapproché de façon tolérante (accents, casse, pluriels). Un nom
 * ambigu renvoie la liste des candidats pour que le LLM demande à l'utilisateur de préciser.</p>
 */
@Slf4j
@Component
public class ProcessTool {

    private final ProcessService processService;

    public ProcessTool(ProcessService processService) {
        this.processService = processService;
    }

    @Tool(name = "get_business_process", description = """
            Charge le déroulement d'un processus métier (étapes, décisions, acteurs responsables) \
            à partir de son nom. À utiliser quand l'utilisateur demande les étapes ou le déroulement \
            d'une procédure (ex. le traitement d'une demande d'adhésion), ou pour vérifier pas à pas \
            si un cas concret remplit les conditions d'un processus, et que ce processus n'est pas \
            déjà présent dans le contexte. Le nom est à choisir dans la liste des processus disponibles. \
            Ne remplace pas la recherche documentaire pour le contenu réglementaire. \
            Retourne le processus encadré par une balise <processus>.""")
    public String getBusinessProcess(
            @ToolParam(description = """
                    Nom du processus, de préférence copié tel quel depuis la liste des processus \
                    disponibles (ex. « CSC AF - Traiter les demandes d'adhésion »).""")
            String processName,
            ToolContext toolContext) {

        Map<String, Object> context = toolContext != null ? toolContext.getContext() : Map.of();
        String language = context.get(ToolContextKeys.CTX_LANGUAGE) instanceof String s && !s.isBlank() ? s : "fr";
        ToolContextKeys.emitStatus(context, ChatStatus.TOOL_USE, language, processName);

        if (!(context.get(ToolContextKeys.CTX_LOADED_PROCESSES) instanceof LoadedProcesses loaded)) {
            return "Le chargement des processus métier n'est pas disponible.";
        }
        if (processName == null || processName.isBlank()) {
            return "Nom de processus manquant : choisissez-en un dans la liste des processus disponibles.";
        }

        ProcessService.NameMatch match = processService.findByName(processName);
        if (!match.candidates().isEmpty()) {
            log.info("get_business_process('{}') ambigu : {}", processName, match.candidates());
            return ("Plusieurs processus correspondent à « %s » : %s. Demandez à l'utilisateur lequel "
                    + "le concerne, puis appelez à nouveau ce tool avec le nom exact.")
                    .formatted(processName, String.join(" ; ", match.candidates()));
        }
        if (!match.found()) {
            log.info("get_business_process('{}') : aucun processus correspondant", processName);
            return ("Aucun processus ne correspond à « %s ». Vérifiez le nom dans la liste des processus "
                    + "disponibles ; si aucun ne convient, répondez à partir de la recherche documentaire.")
                    .formatted(processName);
        }

        BusinessProcess process = match.process();
        if (!loaded.add(process)) {
            return "Le processus « %s » est déjà présent dans le contexte : utilisez-le.".formatted(process.name());
        }
        String block = processService.render(process);
        log.info("Processus injecté (get_business_process) : '{}' ({} caractères)", process.name(), block.length());
        log.debug("Bloc processus injecté :{}{}", System.lineSeparator(), block);
        return block;
    }
}
