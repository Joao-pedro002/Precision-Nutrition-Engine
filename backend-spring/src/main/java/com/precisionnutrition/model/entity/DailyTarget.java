package com.precisionnutrition.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.precisionnutrition.model.dto.MicronutrientsData;
import com.precisionnutrition.model.enums.TargetSource;
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
@Table(name = "daily_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_source", nullable = false, length = 20)
    @Builder.Default
    private TargetSource targetSource = TargetSource.MANUAL;

    @JsonProperty("isActive")
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = false;

    @Column(name = "target_kcal_min", nullable = false, precision = 8, scale = 2)
    private BigDecimal targetKcalMin;

    @Column(name = "target_kcal_max", nullable = false, precision = 8, scale = 2)
    private BigDecimal targetKcalMax;

    @Column(name = "maintenance_kcal_ceiling", nullable = false, precision = 8, scale = 2)
    private BigDecimal maintenanceKcalCeiling;

    @Column(name = "target_protein_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal targetProteinG;

    @Column(name = "target_carbohydrate_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal targetCarbohydrateG;

    @Column(name = "target_lipid_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal targetLipidG;

    @Column(name = "target_fiber_g", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal targetFiberG = new BigDecimal("30.00");

    @Column(name = "target_water_ml", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal targetWaterMl = new BigDecimal("3500.00");

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_micronutrients", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private MicronutrientsData targetMicronutrients = MicronutrientsData.empty();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
