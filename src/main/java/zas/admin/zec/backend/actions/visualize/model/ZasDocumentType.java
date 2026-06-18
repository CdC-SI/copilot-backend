package zas.admin.zec.backend.actions.visualize.model;

public record ZasDocumentType(Type type) {
    public enum Type {ID_CARD, PASSPORT, DRIVER_LICENSE, WORK_PERMIT, SUMEX_INVOICE, TEXT_OF_LAW, VETERINARY_INVOICE, UNKNOWN}
}
