package com.precisionnutrition.service;

import com.precisionnutrition.client.GeminiClient;
import com.precisionnutrition.exception.AiServiceUnavailableException;
import com.precisionnutrition.model.dto.AiChatMessageDto;
import com.precisionnutrition.model.dto.AiChatRequest;
import com.precisionnutrition.model.dto.AiChatResponse;
import com.precisionnutrition.model.dto.DailyLogEntryDto;
import com.precisionnutrition.model.dto.DailySummaryDto;
import com.precisionnutrition.model.dto.DashboardResponse;
import com.precisionnutrition.model.dto.MealDashboardDto;
import com.precisionnutrition.model.dto.MealItemCalculatedDto;
import com.precisionnutrition.model.dto.MealOptionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NutritionAiServiceImpl implements NutritionAiService {

    private static final int MAX_HISTORY_MESSAGES = 12; // Até 6 trocas (user + model)

    private final DashboardService dashboardService;
    private final GeminiClient geminiClient;
    private final AiRateLimiter aiRateLimiter;

    @Override
    public AiChatResponse chat(UUID userId, AiChatRequest request) {
        aiRateLimiter.checkAndConsume(userId);

        if (!geminiClient.isConfigured()) {
            throw new AiServiceUnavailableException(
                    "O Copiloto Nutricional com IA ainda não está configurado neste servidor (GEMINI_API_KEY ausente)."
            );
        }

        LocalDate targetDate = request.date() != null ? request.date() : LocalDate.now();
        DashboardResponse dashboard = dashboardService.getDashboard(userId, targetDate);

        String systemInstruction = buildSystemInstruction(dashboard);
        List<AiChatMessageDto> turns = buildConversationTurns(request);

        log.debug("Consultando Copiloto Nutricional IA para usuário {} na data {} (histórico={} msgs)",
                userId, targetDate, turns.size() - 1);

        String reply = geminiClient.generateContent(systemInstruction, turns);
        return new AiChatResponse(reply, geminiClient.getModel());
    }

    private List<AiChatMessageDto> buildConversationTurns(AiChatRequest request) {
        List<AiChatMessageDto> turns = new ArrayList<>();

        if (request.history() != null && !request.history().isEmpty()) {
            List<AiChatMessageDto> sanitized = request.history().stream()
                    .filter(m -> m != null && m.text() != null && !m.text().isBlank()
                            && ("user".equals(m.role()) || "model".equals(m.role())))
                    .toList();

            int startIndex = Math.max(0, sanitized.size() - MAX_HISTORY_MESSAGES);
            for (int i = startIndex; i < sanitized.size(); i++) {
                AiChatMessageDto item = sanitized.get(i);
                turns.add(new AiChatMessageDto(item.role(), item.text().trim()));
            }
        }

        turns.add(new AiChatMessageDto("user", request.message().trim()));
        return turns;
    }

    private String buildSystemInstruction(DashboardResponse dashboard) {
        DailySummaryDto s = dashboard.summary();
        StringBuilder sb = new StringBuilder();

        sb.append("Você é o Copiloto Nutricional oficial do Precision Nutrition (modo consultivo).\n");
        sb.append("REGRAS ESTRITAS:\n");
        sb.append("1. Responda SEMPRE em português do Brasil, de forma direta, estruturada (Markdown limpo com listas/negrito) e concisa.\n");
        sb.append("2. Atue EXCLUSIVAMENTE como assistente de nutrição, dieta, hidratação e rotina alimentar/treino. Se o usuário perguntar sobre assuntos fora desse escopo (programação, política, finanças, etc.), recuse educadamente e redirecione para o plano alimentar.\n");
        sb.append("3. Use EXATAMENTE os números do CONTEXTO REAL abaixo. NUNCA invente, altere ou estime saldos restantes diferentes dos fornecidos.\n");
        sb.append("4. Ao sugerir refeições, lanches ou substituições, informe combinações práticas com gramagens claras (ex: 150g de peito de frango grelhado + 120g de arroz cozido) e os macros aproximados para bater exatamente o saldo restante respeitando o déficit/meta do usuário.\n");
        sb.append("5. Não prescreva medicamentos nem dê diagnósticos médicos.\n\n");

        sb.append("=== CONTEXTO REAL DO USUÁRIO (DATA: ").append(dashboard.date()).append(") ===\n");

        if (dashboard.activeTarget() == null) {
            sb.append("Protocolo ativo: Nenhuma meta ativa configurada ainda.\n");
        } else {
            sb.append("Protocolo ativo: ")
                    .append(dashboard.activeTarget().getTargetSource() != null ? dashboard.activeTarget().getTargetSource() : "ATIVO")
                    .append("\n");
        }

        if (s != null) {
            BigDecimal kcalConsumed = nz(s.consumedEnergyKcal());
            BigDecimal kcalMin = nz(s.targetKcalMin());
            BigDecimal kcalMax = nz(s.targetKcalMax());
            BigDecimal kcalCeiling = nz(s.maintenanceKcalCeiling());

            BigDecimal pConsumed = nz(s.consumedProteinG());
            BigDecimal pTarget = nz(s.targetProteinG());
            BigDecimal cConsumed = nz(s.consumedCarbohydrateG());
            BigDecimal cTarget = nz(s.targetCarbohydrateG());
            BigDecimal gConsumed = nz(s.consumedLipidG());
            BigDecimal gTarget = nz(s.targetLipidG());
            BigDecimal fConsumed = nz(s.consumedFiberG());
            BigDecimal fTarget = nz(s.targetFiberG());
            BigDecimal wConsumed = nz(s.waterConsumedMl());
            BigDecimal wTarget = nz(s.targetWaterMl());

            sb.append(String.format(Locale.US,
                    "- Calorias: consumido %.0f kcal | faixa alvo %.0f–%.0f kcal | teto manutenção %.0f kcal | saldo até o teto da meta: %s\n",
                    kcalConsumed, kcalMin, kcalMax, kcalCeiling, formatDelta(kcalMax.subtract(kcalConsumed), "kcal")));
            if (s.caloricZone() != null && s.caloricZone().zone() != null) {
                sb.append("- Zona calórica atual: ").append(s.caloricZone().zone().name()).append("\n");
            }
            sb.append(String.format(Locale.US,
                    "- Proteínas: consumido %.1fg / meta %.1fg | %s\n",
                    pConsumed, pTarget, formatDelta(pTarget.subtract(pConsumed), "g")));
            sb.append(String.format(Locale.US,
                    "- Carboidratos: consumido %.1fg / meta %.1fg | %s\n",
                    cConsumed, cTarget, formatDelta(cTarget.subtract(cConsumed), "g")));
            sb.append(String.format(Locale.US,
                    "- Gorduras: consumido %.1fg / meta %.1fg | %s\n",
                    gConsumed, gTarget, formatDelta(gTarget.subtract(gConsumed), "g")));
            sb.append(String.format(Locale.US,
                    "- Fibras: consumido %.1fg / meta %.1fg | %s\n",
                    fConsumed, fTarget, formatDelta(fTarget.subtract(fConsumed), "g")));
            sb.append(String.format(Locale.US,
                    "- Água: consumido %.0f ml / meta %.0f ml | %s\n",
                    wConsumed, wTarget, formatDelta(wTarget.subtract(wConsumed), "ml")));
        }

        sb.append("\n=== REFEIÇÕES DO PLANO NO DIA ===\n");
        List<MealDashboardDto> meals = dashboard.meals();
        if (meals == null || meals.isEmpty()) {
            sb.append("- Nenhuma refeição prescrita cadastrada no plano.\n");
        } else {
            for (MealDashboardDto meal : meals) {
                String timeStr = meal.targetTime() != null ? " (" + meal.targetTime() + ")" : "";
                String statusStr = meal.isCompleted()
                        ? "[CONSUMIDA" + (meal.completedOptionNumber() != null ? " - Opção " + meal.completedOptionNumber() : "") + "]"
                        : "[PENDENTE]";
                sb.append("- ").append(meal.name()).append(timeStr).append(" ").append(statusStr);

                if (meal.options() != null && !meal.options().isEmpty()) {
                    MealOptionDto opt = meal.options().get(0);
                    sb.append(String.format(Locale.US,
                            " | Opção %d: %.0f kcal (P:%.0fg C:%.0fg G:%.0fg)",
                            opt.optionNumber(),
                            nz(opt.totalEnergyKcal()),
                            nz(opt.totalProteinG()),
                            nz(opt.totalCarbohydrateG()),
                            nz(opt.totalLipidG())));
                    if (opt.items() != null && !opt.items().isEmpty()) {
                        sb.append(" -> ");
                        for (int i = 0; i < opt.items().size(); i++) {
                            MealItemCalculatedDto item = opt.items().get(i);
                            if (i > 0) sb.append(", ");
                            sb.append(String.format(Locale.US, "%.0fg %s", nz(item.weightG()), item.foodName()));
                        }
                    }
                }
                sb.append("\n");
            }
        }

        if (dashboard.loggedEntries() != null) {
            List<DailyLogEntryDto> offDiet = dashboard.loggedEntries().stream()
                    .filter(DailyLogEntryDto::isOffDiet)
                    .toList();
            if (!offDiet.isEmpty()) {
                sb.append("\n=== REGISTROS FORA DA DIETA HOJE ===\n");
                for (DailyLogEntryDto entry : offDiet) {
                    sb.append(String.format(Locale.US,
                            "- %s (%.0fg): %.0f kcal (P:%.1fg C:%.1fg G:%.1fg)\n",
                            entry.foodNameSnapshot(),
                            nz(entry.weightConsumedG()),
                            nz(entry.consumedEnergyKcal()),
                            nz(entry.consumedProteinG()),
                            nz(entry.consumedCarbohydrateG()),
                            nz(entry.consumedLipidG())));
                }
            }
        }

        return sb.toString();
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String formatDelta(BigDecimal remaining, String unit) {
        BigDecimal rounded = remaining.setScale(1, RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.ZERO) > 0) {
            return String.format(Locale.US, "faltam %.1f %s", rounded, unit);
        }
        if (rounded.compareTo(BigDecimal.ZERO) < 0) {
            return String.format(Locale.US, "excedeu em %.1f %s", rounded.abs(), unit);
        }
        return "meta atingida (0 " + unit + " restante)";
    }
}
