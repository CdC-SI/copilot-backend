package zas.admin.zec.backend.actions.upload.model;

import java.time.LocalDateTime;

public record PersonalDoc(
   String title,
   LocalDateTime uploadedAt
) {}
