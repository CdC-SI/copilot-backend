package zas.admin.zec.backend.actions.authorize;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateRolesRequest(
        @NotNull(message = "La liste des rôles ne peut pas être null")
        @NotEmpty(message = "La liste des rôles ne peut pas être vide")
        List<String> roles
) {}
