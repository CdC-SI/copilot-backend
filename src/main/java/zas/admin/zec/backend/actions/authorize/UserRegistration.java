package zas.admin.zec.backend.actions.authorize;

import java.util.List;

public record UserRegistration(String firstName, String lastName, List<String> organizations) {}
