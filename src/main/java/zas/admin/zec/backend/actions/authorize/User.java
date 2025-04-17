package zas.admin.zec.backend.actions.authorize;

import java.util.List;

public record User(
    String username,
    List<Role> roles,
    List<String> organizations
) {}
