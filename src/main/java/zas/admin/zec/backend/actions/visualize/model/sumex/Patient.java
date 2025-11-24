package zas.admin.zec.backend.actions.visualize.model.sumex;

public record Patient(
        String lastName,
        String firstName,
        String street,
        String postalCode,
        String locality,
        String poBox,
        String country,
        String birthday,
        String gender,
        String accidentDate,
        String insuredPersonNumber,
        String caseNumber,
        String avsNumber
) {
}
