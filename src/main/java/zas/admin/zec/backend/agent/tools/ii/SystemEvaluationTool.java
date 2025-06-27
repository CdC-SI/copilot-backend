package zas.admin.zec.backend.agent.tools.ii;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import zas.admin.zec.backend.agent.tools.ii.model.SystemEvaluation;

public class SystemEvaluationTool {

    @Tool(
            name = "collectEvaluation",
            description = "Renseigne les 13 indicateurs pour déterminer le système à appliquer à la demande. Chaque propriété doit valoir true, false ou null (null = information absente dans la conversation).",
            returnDirect = true
    )
    public SystemEvaluation collectEvaluation(
            @ToolParam(description = "True si le système de rente à utiliser est déjà mentionné dans la conversation (Rente linéaire ou Rente par pallier)") boolean systemAlreadyEvaluated,
            @ToolParam(description = "True si le taux a augmenté depuis le 01.01.2024, false sinon, null si pas d'information") Boolean rateUpSince2024,
            @ToolParam(description = "True si changement de palier selon l'ancien système, false sinon, null si pas d'information") Boolean legacyTierChange,
            @ToolParam(description = "True si révision (sur demande ou d'office), false si nouvelle demande, null si pas d'information") Boolean revisionCase,
            @ToolParam(description = "True si 1ère demande RER déposée avant le 01.07.2021 et échéance délai de carence avant le 01.01.2022, false sinon, null si pas d'information") Boolean rerBeforeJul2021,
            @ToolParam(description = "True si modification des faits entre le 01.01.2022 et le 31.12.2023, false sinon, null si pas d'information") Boolean factsChanged,
            @ToolParam(description = "True si modification ≥ 5 % du degré d'invalidité, false si modification < 5%, null si pas d'information") Boolean invalidityDelta5,
            @ToolParam(description = "True si augmentation du degré d'invalidité, false si diminution du degré ou pas d’évolution, null si pas d'information") Boolean invalidityIncrease,
            @ToolParam(description = "True si l'assuré avait ≥ 55 ans le 01.01.2022, false sinon, null si pas d'information") Boolean age55On2022,
            @ToolParam(description = "True si taux d'invalidité ≥ 50 %, false sinon, null si pas d'information") Boolean invalidity50Plus,
            @ToolParam(description = "True si diminution du montant de la rente, false sinon, null si pas d'information") Boolean pensionDecrease,
            @ToolParam(description = "True si taux d'invalidité ≥ 70 %, false sinon, null si pas d'information") Boolean invalidity70Plus,
            @ToolParam(description = "True si droit ouvert dans le système linéaire, false sinon, null si pas d'information") Boolean linearEntitlement,
            @ToolParam(description = "True si augmentation du montant de la rente, false sinon, null si pas d'information") Boolean pensionIncrease) {

        return new SystemEvaluation(
                systemAlreadyEvaluated,
                rateUpSince2024,
                legacyTierChange,
                revisionCase,
                rerBeforeJul2021,
                factsChanged,
                invalidityDelta5,
                invalidityIncrease,
                age55On2022,
                invalidity50Plus,
                pensionDecrease,
                invalidity70Plus,
                linearEntitlement,
                pensionIncrease);
    }
}
