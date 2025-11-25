package zas.admin.zec.backend.actions.visualize.model.sumex;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

public record SumexInvoice(
        InvoiceAuthor author,
        Patient patient,
        InvoiceMetaData metaData,
        List<MedicalService> medicalServices,
        PaymentInformation paymentInformation,
        @DefaultValue("0.0") Double totalAmount
) {}