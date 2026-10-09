package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Activation du chargement des processus métier BPMN dans le contexte du LLM : enchaînement
 * automatique depuis les documents retrouvés par {@code RAGTool}, tool {@code get_business_process}
 * et réinjection d'un processus d'un tour à l'autre. Désactivé, le comportement est inchangé.
 */
@ConfigurationProperties(prefix = "ai.process")
public record ProcessProperties(boolean enabled) {}
