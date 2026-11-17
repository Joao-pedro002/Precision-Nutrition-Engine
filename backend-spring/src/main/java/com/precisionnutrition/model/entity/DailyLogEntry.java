package com.precisionnutrition.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.precisionnutrition.model.dto.MicronutrientsData;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_log_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_log_id", nullable = false)
    @JsonBackReference
    private DailyLog dailyLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id")
    @JsonIgnoreProperties({"items", "hibernateLazyInitializer", "handler"})
    private Meal meal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Food food;

    @Column(name = "food_name_snapshot", nullable = false, length = 255)
    private String foodNameSnapshot;

    @Column(name = "weight_consumed_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal weightConsumedG;

    @Column(name = "consumed_energy_kcal", nullable = false, precision = 8, scale = 2)
    private BigDecimal consumedEnergyKcal;

    @Column(name = "consumed_protein_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal consumedProteinG;

    @Column(name = "consumed_carbohydrate_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal consumedCarbohydrateG;

    @Column(name = "consumed_lipid_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal consumedLipidG;

    @Column(name = "consumed_fiber_g", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal consumedFiberG = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consumed_micronutrients", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private MicronutrientsData consumedMicronutrients = MicronutrientsData.empty();

    @JsonProperty("isOffDiet")
    @Column(name = "is_off_diet", nullable = false)
    @Builder.Default
    private boolean isOffDiet = false;

    @CreationTimestamp
    @Column(name = "logged_at", nullable = false, updatable = false)
    private OffsetDateTime loggedAt;
}
