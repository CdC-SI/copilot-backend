package zas.admin.zec.backend.actions.upload.strategy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.entity.QuestionEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.QuestionRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class EmbeddedDocUploadStrategy implements UploadStrategy {

    private static final int BATCH_SIZE = 1000;
    private static final String COL_CONTENT = "content";
    private static final String COL_METADATA = "metadata";
    private static final String COL_EMBEDDING = "embedding";
    private static final String ERR_UPLOAD_CSV = "Error while uploading CSV";
    private static final CSVFormat CSV_FMT = CSVFormat.DEFAULT
            .builder()
            .setHeader(COL_CONTENT, COL_METADATA, COL_EMBEDDING)
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .build();

    private final DocumentRepository documentRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper mapper;

    public EmbeddedDocUploadStrategy(DocumentRepository documentRepository, QuestionRepository questionRepository) {
        this.documentRepository = documentRepository;
        this.questionRepository = questionRepository;
        this.mapper = new ObjectMapper();
        this.mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    @Override
    public void upload(DocumentToUpload doc) {
        try (Reader in = new InputStreamReader(new ByteArrayInputStream(doc.file().getBytes()), StandardCharsets.UTF_8)) {
            if (doc.faqStore()) processQuestionCSV(in);
            else processDocumentCSV(in);
        } catch (IOException e) {
            throw new UploadException(doc.file().getOriginalFilename(), ERR_UPLOAD_CSV, e);
        }
    }

    private void processQuestionCSV(Reader in) throws IOException {
        List<QuestionEntity> buffer = new ArrayList<>(BATCH_SIZE);
        for (CSVRecord rec : CSV_FMT.parse(in)) {
            buffer.add(toQuestionEntity(rec));
            if (buffer.size() == BATCH_SIZE) {
                flushBatch(buffer, questionRepository);
            }
        }
        if (!buffer.isEmpty()) {
            flushBatch(buffer, questionRepository);
        }
    }

    private void processDocumentCSV(Reader in) throws IOException {
        List<DocumentEntity> buffer = new ArrayList<>(BATCH_SIZE);
        for (CSVRecord rec : CSV_FMT.parse(in)) {
            buffer.add(toDocumentEntity(rec));
            if (buffer.size() == BATCH_SIZE) {
                flushBatch(buffer, documentRepository);
            }
        }
        if (!buffer.isEmpty()) {
            flushBatch(buffer, documentRepository);
        }
    }

    private DocumentEntity toDocumentEntity(CSVRecord rec) throws JsonProcessingException {
        DocumentEntity entity = new DocumentEntity();
        setCommonFields(entity, rec);
        return entity;
    }

    private QuestionEntity toQuestionEntity(CSVRecord rec) throws JsonProcessingException {
        QuestionEntity entity = new QuestionEntity();
        setCommonFields(entity, rec);
        return entity;
    }

    private void setCommonFields(Object entity, CSVRecord rec) throws JsonProcessingException {
        if (entity instanceof DocumentEntity doc) {
            doc.setContent(rec.get(COL_CONTENT));
            doc.setMetadata(mapper.readValue(rec.get(COL_METADATA), new TypeReference<>() {}));
            doc.setEmbedding(parseEmbedding(rec.get(COL_EMBEDDING)));
        } else if (entity instanceof QuestionEntity question) {
            question.setContent(rec.get(COL_CONTENT));
            question.setMetadata(mapper.readValue(rec.get(COL_METADATA), new TypeReference<>() {}));
            question.setEmbedding(parseEmbedding(rec.get(COL_EMBEDDING)));
        }
    }

    private <T> void flushBatch(List<T> batch, JpaRepository<T, ?> repository) {
        repository.saveAll(batch);
        repository.flush();
        batch.clear();
    }

    public static float[] parseEmbedding(String field) {
        String[] parts = field.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }
}
