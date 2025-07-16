package zas.admin.zec.backend.actions.upload.strategy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;

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

    private final DocumentRepository documentRepository;
    private final ObjectMapper mapper;

    public EmbeddedDocUploadStrategy(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
        this.mapper = new ObjectMapper();
        this.mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    @Override
    public void upload(DocumentToUpload doc) {
        try (Reader in = new InputStreamReader(new ByteArrayInputStream(doc.content()), StandardCharsets.UTF_8)) {
            List<DocumentEntity> buffer = new ArrayList<>(BATCH_SIZE);
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
            throw new UploadException("Error while uploading CSV " + doc.name(), e);
        }
    }

    private DocumentEntity toEntity(CSVRecord rec) throws JsonProcessingException {
        DocumentEntity entity = new DocumentEntity();
        entity.setContent(rec.get("content"));
        entity.setMetadata(mapper.readValue(rec.get("metadata"), new TypeReference<>() {}));
        entity.setEmbedding(parseEmbedding(rec.get("embedding")));

        return entity;
    }

    private void flushBatch(List<DocumentEntity> batch) {
        documentRepository.saveAll(batch);
        documentRepository.flush();
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
