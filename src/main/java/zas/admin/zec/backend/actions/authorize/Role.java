package zas.admin.zec.backend.actions.authorize;

public enum Role {
    ADMIN,
    EXPERT,
    TRANSLATOR,
    USER;

    public static Role from(String role) {
        return switch (role) {
            case "ADMIN" -> ADMIN;
            case "USER" -> USER;
            case "EXPERT" -> EXPERT;
            case "TRANSLATOR" -> TRANSLATOR;
            default -> throw new IllegalArgumentException("Invalid roles: " + role);
        };
    }
}
