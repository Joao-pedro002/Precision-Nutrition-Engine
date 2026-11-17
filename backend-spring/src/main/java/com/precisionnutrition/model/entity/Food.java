package com.precisionnutrition.model.entity;

import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.enums.DominantMacro;
import com.precisionnutrition.model.enums.FoodSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "foods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodSource source;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "dominant_macro", nullable = false, length = 20)
    private DominantMacro dominantMacro;

    @Column(name = "serving_size_g", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal servingSizeG = new BigDecimal("100.00");

    @Column(name = "energy_kcal", nullable = false, precision = 8, scale = 2)
    private BigDecimal energyKcal;

    @Column(name = "protein_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "carbohydrate_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbohydrateG;

    @Column(name = "lipid_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal lipidG;

    @Column(name = "fiber_g", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal fiberG = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "micronutrients", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private MicronutrientsData micronutrients = MicronutrientsData.empty();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
