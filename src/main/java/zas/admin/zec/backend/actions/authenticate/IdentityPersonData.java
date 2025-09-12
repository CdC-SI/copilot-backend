package zas.admin.zec.backend.actions.authenticate;

public record IdentityPersonData(
    String userAnonymousId,
    String firstname,
    String lastname,
    String correspondingLang,
    String dateOfBirth,
    String street,
    String streetNr,
    String zip,
    String city,
    String country,
    String email,
    String mobilePhone,
    String nationality,
    String idNumber,
    String idType,
    String gender
) {}

