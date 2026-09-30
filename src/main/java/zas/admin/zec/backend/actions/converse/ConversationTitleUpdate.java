package zas.admin.zec.backend.actions.converse;

import com.fasterxml.jackson.annotation.JsonAlias;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConversationTitleUpdate(
        @JsonAlias("newTitle") String newTitle
) {}
