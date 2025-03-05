package zas.admin.zec.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FAQItemRepository extends JpaRepository<FAQItemEntity, Integer> {

    @Query(
            value = """
                    SELECT *
                    FROM question q
                    WHERE word_similarity(:userInput, q.text) > :threshold
                    ORDER BY word_similarity(:userInput, q.text) DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<FAQItemEntity> findByWordSimilarity(
            @Param("userInput") String userInput,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    @Query(
            value = """
                    SELECT *
                    FROM question q
                    ORDER BY q.text_embedding <=> CAST(:textEmbedding AS vector(1536))
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<FAQItemEntity> findNearestsByTextEmbedding(
            @Param("textEmbedding") String textEmbedding,
            @Param("limit") int limit
    );
}
