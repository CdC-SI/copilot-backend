package zas.admin.zec.backend.actions.authorize;

import java.util.List;

public record UserProfile(String username, String firstName, String lastName, UserStatus status, List<String> roles) {}
