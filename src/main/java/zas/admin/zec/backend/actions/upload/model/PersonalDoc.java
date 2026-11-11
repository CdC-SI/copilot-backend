package zas.admin.zec.backend.actions.upload.model;

import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;

import java.time.LocalDateTime;

public record PersonalDoc(
   String title,
   LocalDateTime uploadedAt,
   EmbeddingStatus status,
   AvailabilityStatus availabilityStatus,
   Long timeToLiveInDays,
   UploadErrorCode errorCode
) {}
