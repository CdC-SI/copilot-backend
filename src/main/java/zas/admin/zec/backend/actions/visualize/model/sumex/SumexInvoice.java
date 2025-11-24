package zas.admin.zec.backend.actions.visualize.model.sumex;

import java.util.List;

public record SumexInvoice(
        InvoiceAuthor author,
        Patient patient,
        InvoiceMetaData metaData,
        List<MedicalService> medicalServices,
        PaymentInformation paymentInformation,
        Double totalAmount
) {}