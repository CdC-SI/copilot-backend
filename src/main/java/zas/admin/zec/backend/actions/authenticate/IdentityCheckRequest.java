package zas.admin.zec.backend.actions.authenticate;

public record IdentityCheckRequest(
        IdentityPersonData userdata,
        String identificationMethod,
        String redirectUrl,
        boolean gtcAccepted,
        boolean background
) {
}
