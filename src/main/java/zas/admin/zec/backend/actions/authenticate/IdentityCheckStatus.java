package zas.admin.zec.backend.actions.authenticate;

import java.util.UUID;

public record IdentityCheckStatus(
        UUID id,
        String state,
        String identificationMethod,
        String timestamp,
        IdentityPersonData userdata
) {}