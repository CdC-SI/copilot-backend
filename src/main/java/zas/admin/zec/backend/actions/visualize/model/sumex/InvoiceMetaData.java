package zas.admin.zec.backend.actions.visualize.model.sumex;

import java.time.LocalDate;

public record InvoiceMetaData(
        String type,
        String treatmentFrom,
        String treatmentTo,
        String treatmentCause,
        LocalDate creationDate,
        String invoiceNumber
) { }
