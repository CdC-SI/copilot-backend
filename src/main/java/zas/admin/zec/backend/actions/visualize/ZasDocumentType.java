package zas.admin.zec.backend.actions.visualize;

public record ZasDocumentType(Type type) {
    enum Type {ID_CARD, PASSPORT, DRIVER_LICENSE, WORK_PERMIT, SUMEX_INVOICE, TEXT_OF_LAW, VETERINARY_INVOICE, UNKNOWN}
}
