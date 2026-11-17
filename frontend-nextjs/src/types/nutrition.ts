export type DominantMacro = 'PROTEIN' | 'CARB' | 'FAT' | 'BALANCED';

export type CalorieZone = 'BELOW_TARGET' | 'OPTIMAL_DEFICIT' | 'MAINTENANCE_MARGIN' | 'SURPLUS';

export interface CaloricZoneResult {
  consumedKcal: number;
  targetKcalMin: number;
  targetKcalMax: number;
  maintenanceKcalCeiling: number;
  zone: CalorieZone;
  deltaToTargetMin: number;
  deltaToMaintenanceCeiling: number;
}

export interface MicronutrientsData {
  calcium_mg: number;
  iron_mg: number;
  magnesium_mg: number;
  potassium_mg: number;
  sodium_mg: number;
  zinc_mg: number;
  vitamin_c_mg: number;
  vitamin_b12_mcg: number;
  cholesterol_mg: number;
}

export interface MicronutrientComparisonDto {
  nutrientKey: string;
  displayName: string;
  unit: string;
  consumedAmount: number;
  targetAmount: number;
  percentageOfTarget: number;
}

export interface MicronutrientMatrixDto {
  sodiumPotassiumRatio: number | null;
  sodiumPotassiumStatus: string;
  comparisons: MicronutrientComparisonDto[];
}

export interface DailySummaryDto {
  consumedEnergyKcal: number;
  targetKcalMin: number;
  targetKcalMax: number;
  maintenanceKcalCeiling: number;
  caloricZone: CaloricZoneResult;
  consumedProteinG: number;
  targetProteinG: number;
  proteinPercentage: number;
  consumedCarbohydrateG: number;
  targetCarbohydrateG: number;
  carbohydratePercentage: number;
  consumedLipidG: number;
  targetLipidG: number;
  lipidPercentage: number;
  consumedFiberG: number;
  targetFiberG: number;
  waterConsumedMl: number;
  targetWaterMl: number;
  waterPercentage: number;
}

export interface DailyTarget {
  id: string;
  userId: string;
  isActive: boolean;
  targetKcalMin: number;
  targetKcalMax: number;
  maintenanceKcalCeiling: number;
  targetProteinG: number;
  targetCarbohydrateG: number;
  targetLipidG: number;
  targetFiberG: number;
  targetWaterMl: number;
  targetMicronutrients: MicronutrientsData;
}

export interface MealItemCalculated {
  id: string;
  foodId: string;
  foodName: string;
  weightG: number;
  energyKcal: number;
  proteinG: number;
  carbohydrateG: number;
  lipidG: number;
  fiberG: number;
  micronutrients: MicronutrientsData;
}

export interface MealOption {
  optionNumber: number;
  totalEnergyKcal: number;
  totalProteinG: number;
  totalCarbohydrateG: number;
  totalLipidG: number;
  totalFiberG: number;
  items: MealItemCalculated[];
}

export interface MealDashboard {
  id: string;
  name: string;
  orderIndex: number;
  targetTime: string;
  isCompleted: boolean;
  completedOptionNumber: number | null;
  options: MealOption[];
}

export interface DailyLogEntry {
  id: string;
  mealId: string | null;
  foodId: string;
  foodNameSnapshot: string;
  weightConsumedG: number;
  consumedEnergyKcal: number;
  consumedProteinG: number;
  consumedCarbohydrateG: number;
  consumedLipidG: number;
  consumedFiberG: number;
  consumedMicronutrients: MicronutrientsData;
  isOffDiet: boolean;
  loggedAt: string;
}

export interface DashboardResponse {
  date: string;
  activeTarget: DailyTarget | null;
  summary: DailySummaryDto;
  micronutrients: MicronutrientMatrixDto;
  meals: MealDashboard[];
  loggedEntries: DailyLogEntry[];
}

export interface WaterUpdateRequest {
  mode: 'INCREMENT' | 'SET';
  amountMl: number;
  userId?: string;
}

export interface WaterUpdateResponse {
  date: string;
  waterConsumedMl: number;
  waterTargetMl: number;
  waterPercent: number;
}

export interface Food {
  id: string;
  name: string;
  source: 'TACO' | 'USDA' | 'CUSTOM';
  externalId: string | null;
  category: string | null;
  dominantMacro: DominantMacro;
  servingSizeG: number;
  energyKcal: number;
  proteinG: number;
  carbohydrateG: number;
  lipidG: number;
  fiberG: number;
  micronutrients: MicronutrientsData;
}

export interface FoodSummary {
  id: string;
  name: string;
  weightG: number;
  energyKcal: number;
  proteinG: number;
  carbohydrateG: number;
  lipidG: number;
  fiberG: number;
}

export interface SubstitutionRequest {
  sourceFoodId: string;
  sourceWeightG: number;
  targetFoodId: string;
  targetMacroOverride?: DominantMacro | 'KCAL' | null;
}

export interface SubstitutionResult {
  sourceFood: FoodSummary;
  targetFood: FoodSummary;
  deltaKcal: number;
  deltaProteinG: number;
  deltaCarbohydrateG: number;
  deltaLipidG: number;
  calorieDriftPercent: number;
  calorieDriftWarning: boolean;
  warningMessage: string | null;
  matchedMacro: string;
}

export interface CheckMealRequest {
  mealId: string;
  optionNumber: number;
  userId?: string;
}

export interface OffDietEntryRequest {
  foodId?: string | null;
  customName?: string;
  weightConsumedG: number;
  energyKcal: number;
  proteinG: number;
  carbohydrateG: number;
  lipidG: number;
  fiberG: number;
  userId?: string;
}

