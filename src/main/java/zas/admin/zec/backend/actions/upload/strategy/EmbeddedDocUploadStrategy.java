package zas.admin.zec.backend.actions.upload.strategy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.InternalDocumentEntity;
import zas.admin.zec.backend.persistence.repository.InternalDocumentRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class EmbeddedDocUploadStrategy implements UploadStrategy {

    private static final int BATCH_SIZE = 1000;
    private static final CSVFormat CSV_FMT = CSVFormat.DEFAULT
            .builder()
            .setHeader("content", "metadata", "embedding")
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build();

    private final InternalDocumentRepository internalDocumentRepository;
    private final ObjectMapper mapper;

    public EmbeddedDocUploadStrategy(InternalDocumentRepository internalDocumentRepository) {
        this.internalDocumentRepository = internalDocumentRepository;
        this.mapper = new ObjectMapper();
        this.mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    @Override
    public void upload(DocumentToUpload doc) {
        try (Reader in = new InputStreamReader(new ByteArrayInputStream(doc.file().getBytes()), StandardCharsets.UTF_8)) {
            List<InternalDocumentEntity> buffer = new ArrayList<>(BATCH_SIZE);
            for (CSVRecord rec : CSV_FMT.parse(in)) {
                buffer.add(toEntity(rec));
                if (buffer.size() == BATCH_SIZE) {
                    flushBatch(buffer);
                }
            }
            if (!buffer.isEmpty()) {
                flushBatch(buffer);
            }
        } catch (IOException e) {
            throw new UploadException(doc.file().getOriginalFilename(), "Error while uploading CSV", e);
        }
    }

    private InternalDocumentEntity toEntity(CSVRecord rec) throws JsonProcessingException {
        InternalDocumentEntity entity = new InternalDocumentEntity();
        entity.setContent(rec.get("content"));
        entity.setMetadata(mapper.readValue(rec.get("metadata"), new TypeReference<>() {}));
        entity.setEmbedding(parseEmbedding(rec.get("embedding")));

        return entity;
    }

    private void flushBatch(List<InternalDocumentEntity> batch) {
        internalDocumentRepository.saveAll(batch);
        internalDocumentRepository.flush();
        batch.clear();
    }

    private static float[] parseEmbedding(String field) {
        String[] parts = field.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }
}
