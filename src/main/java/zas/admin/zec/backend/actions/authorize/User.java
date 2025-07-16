package zas.admin.zec.backend.actions.authorize;

import java.util.List;

public record User(
    String username,
    String firstName,
    String lastName,
    UserStatus status,
    List<Role> roles,
    List<String> organizations,
    boolean internalUser
) {}
