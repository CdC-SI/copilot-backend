package zas.admin.zec.backend.actions.visualize.model.sumex;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record MedicalService(
        String date,
        String tariff,
        String code,
        String description,
        @DefaultValue("1") Double quantity,
        Double amount
) {}
