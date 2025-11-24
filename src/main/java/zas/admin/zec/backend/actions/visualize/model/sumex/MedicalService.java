package zas.admin.zec.backend.actions.visualize.model.sumex;

public record MedicalService(
        String date,
        String tariff,
        String code,
        String description,
        String quantity,
        Double amount
) {
}
