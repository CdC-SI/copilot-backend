package zas.admin.zec.backend.users;

public enum Role {
    ADMIN,
    USER;

    public static Role from(String role) {
        return switch (role) {
            case "ADMIN" -> ADMIN;
            case "USER" -> USER;
            default -> throw new IllegalArgumentException("Invalid roles: " + role);
        };
    }
}
