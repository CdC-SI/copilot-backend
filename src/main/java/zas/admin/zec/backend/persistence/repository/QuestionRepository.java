package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zas.admin.zec.backend.persistence.entity.QuestionEntity;

import java.util.List;

public interface QuestionRepository extends JpaRepository<QuestionEntity, Integer> {

    @Query(
            value = """
                    SELECT *
                    FROM question q
                    WHERE word_similarity(:userInput, q.content) > :threshold
                    ORDER BY word_similarity(:userInput, q.content) DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<QuestionEntity> findByWordSimilarity(
            @Param("userInput") String userInput,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    @Query(
            value = """
                    SELECT *
                    FROM question q
                    ORDER BY q.embedding <=> CAST(:textEmbedding AS vector(1024))
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<QuestionEntity> findNearestByTextEmbedding(
            @Param("textEmbedding") String textEmbedding,
            @Param("limit") int limit
    );
}
