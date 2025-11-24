package zas.admin.zec.backend.actions.visualize.model.sumex;

public record PaymentInformation(
        String currency,
        String transferType,
        String iban,
        String reference,
        String additionalInfo,
        String name,
        String street,
        String country,
        String postalCode,
        String locality,
        String bvr,
        String bicSwift
) {
}
