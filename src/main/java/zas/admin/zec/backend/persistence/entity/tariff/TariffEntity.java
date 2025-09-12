package zas.admin.zec.backend.persistence.entity.tariff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "tariffs_ai")
public class TariffEntity {

    @Id
    @Column(name = "number", nullable = false)
    private Integer id;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
