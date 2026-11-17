"""
===============================================================================
PRECISION NUTRITION ENGINE — ETL PIPELINE: TABELA TACO (4ª EDIÇÃO)
===============================================================================
Engenharia de Dados Resiliente em 3 Níveis para Ingestão e Normalização de Alimentos.
- Nível 1: Detecção e leitura de arquivo local em etl-python/raw-data/ (.json, .csv, .xlsx)
- Nível 2: Download automatizado via repositório público estável (mirror TACO 4ª ed. com 597 itens)
- Nível 3: Seed mandatório de alimentos básicos essenciais (Staple Foods) da TACO
- Normalização: Limpeza de 'Tr', 'NA', '*', '-', comas decimais -> NUMERIC(8,2)
- Regras de Negócio: Cálculo de macro dominante e montagem estrita de JSONB
- Persistência: Upsert em lote idempotente via SQLAlchemy + psycopg2-binary
===============================================================================
"""

import os
import sys
import json
import logging
import re
from typing import Dict, Any, List, Optional
import requests
import pandas as pd
import numpy as np
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

# -----------------------------------------------------------------------------
# CONFIGURAÇÃO DE LOGGING
# -----------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("ETL_TACO")

# -----------------------------------------------------------------------------
# VARIÁVEIS DE AMBIENTE E CONEXÃO POSTGRESQL (DOCKER CONTAINER)
# -----------------------------------------------------------------------------
DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DB_PORT = os.getenv("DB_PORT", "5433")
DB_NAME = os.getenv("DB_NAME", "precision_nutrition")
DB_USER = os.getenv("DB_USER", "precision_user")
DB_PASS = os.getenv("DB_PASS", "precision_pass")

DATABASE_URI = f"postgresql+psycopg2://{DB_USER}:{DB_PASS}@{DB_HOST}:{DB_PORT}/{DB_NAME}"

# Diretório base para arquivos brutos
RAW_DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "raw-data")

# -----------------------------------------------------------------------------
# LIMPEZA NUMÉRICA E CONVERSÕES RÍGIDAS
# -----------------------------------------------------------------------------
NON_NUMERIC_PATTERN = re.compile(r"^(tr|traços|tracos|na|n/a|\*|-|nd|nan|none|null|\s*)$", re.IGNORECASE)

def clean_num(val: Any) -> float:
    """
    Normaliza strings não numéricas da TACO ('Tr', 'NA', '*', '-', vazios e vírgulas)
    para floats rigorosos arredondados em 2 casas decimais.
    """
    if val is None or pd.isna(val):
        return 0.0

    if isinstance(val, (int, float)):
        if np.isnan(val) or np.isinf(val) or val < 0.0:
            return 0.0
        return round(float(val), 2)

    val_str = str(val).strip()
    if NON_NUMERIC_PATTERN.match(val_str):
        return 0.0

    # Substitui vírgula decimal brasileira por ponto
    val_str = val_str.replace(",", ".")
    # Remove qualquer caractere que não seja dígito, ponto ou sinal negativo
    val_str = re.sub(r"[^\d.-]", "", val_str)

    try:
        cleaned = float(val_str)
        if cleaned < 0.0:
            # Clamping científico: análise por diferença (proximate analysis)
            # na TACO pode gerar resíduos negativos espúrios em carnes/peixes
            return 0.0
        return round(cleaned, 2)
    except (ValueError, TypeError):
        return 0.0

# -----------------------------------------------------------------------------
# MOTOR DE CLASSIFICAÇÃO DE MACRO DOMINANTE
# -----------------------------------------------------------------------------
def classify_dominant_macro(protein_g: float, carb_g: float, lipid_g: float) -> str:
    """
    Classifica o macro dominante do alimento ('PROTEIN', 'CARB', 'FAT', 'BALANCED')
    com base na densidade calórica relativa segundo os fatores de Atwater:
    - Proteína: 4 kcal/g
    - Carboidrato: 4 kcal/g
    - Gordura: 9 kcal/g
    """
    p_kcal = protein_g * 4.0
    c_kcal = carb_g * 4.0
    f_kcal = lipid_g * 9.0
    total_kcal = p_kcal + c_kcal + f_kcal

    if total_kcal <= 0.0:
        return "BALANCED"

    p_share = p_kcal / total_kcal
    c_share = c_kcal / total_kcal
    f_share = f_kcal / total_kcal

    # Regras de corte nutricional
    if p_share >= 0.40 or (p_share > c_share and p_share > f_share and p_share >= 0.35):
        return "PROTEIN"
    elif c_share >= 0.50 or (c_share > p_share and c_share > f_share and c_share >= 0.45):
        return "CARB"
    elif f_share >= 0.45 or (f_share > p_share and f_share > c_share and f_share >= 0.40):
        return "FAT"
    else:
        return "BALANCED"

# -----------------------------------------------------------------------------
# CONSTRUTOR DE MICRONUTRIENTES JSONB (9 CHAVES ESTRITAS)
# -----------------------------------------------------------------------------
def build_micronutrients(
    calcium_mg: float = 0.0,
    iron_mg: float = 0.0,
    magnesium_mg: float = 0.0,
    potassium_mg: float = 0.0,
    sodium_mg: float = 0.0,
    zinc_mg: float = 0.0,
    vitamin_c_mg: float = 0.0,
    vitamin_b12_mcg: float = 0.0,
    cholesterol_mg: float = 0.0,
) -> Dict[str, float]:
    """
    Gera o dicionário JSONB respeitando estritamente o contrato de 01_Data_Contracts_DDL_JSONB.md.
    """
    return {
        "calcium_mg": round(calcium_mg, 2),
        "iron_mg": round(iron_mg, 2),
        "magnesium_mg": round(magnesium_mg, 2),
        "potassium_mg": round(potassium_mg, 2),
        "sodium_mg": round(sodium_mg, 2),
        "zinc_mg": round(zinc_mg, 2),
        "vitamin_c_mg": round(vitamin_c_mg, 2),
        "vitamin_b12_mcg": round(vitamin_b12_mcg, 2),
        "cholesterol_mg": round(cholesterol_mg, 2),
    }

# -----------------------------------------------------------------------------
# NÍVEL 3: BASE DE ALIMENTOS BÁSICOS OBRIGATÓRIOS DA DIETA BRASILEIRA (TACO 4ª ED.)
# -----------------------------------------------------------------------------
def get_staple_foods_seed() -> List[Dict[str, Any]]:
    """
    Base de dados curada com os principais alimentos da dieta brasileira,
    com laudos laboratoriais exatos da TACO 4ª Edição (Base 100g).
    Garante a operabilidade perfeita do sistema mesmo em ausência de rede.
    """
    staples = [
        # Cereais e Leguminosas
        {
            "external_id": "TACO-001",
            "name": "Arroz, tipo 1, cozido",
            "category": "Cereais e leguminosas",
            "energy_kcal": 128.00,
            "protein_g": 2.50,
            "carbohydrate_g": 28.10,
            "lipid_g": 0.20,
            "fiber_g": 1.60,
            "micronutrients": build_micronutrients(
                calcium_mg=4.0, iron_mg=0.1, magnesium_mg=2.0, potassium_mg=14.0, sodium_mg=1.0, zinc_mg=0.7
            ),
        },
        {
            "external_id": "TACO-002",
            "name": "Arroz, integral, cozido",
            "category": "Cereais e leguminosas",
            "energy_kcal": 124.00,
            "protein_g": 2.60,
            "carbohydrate_g": 25.80,
            "lipid_g": 1.00,
            "fiber_g": 2.70,
            "micronutrients": build_micronutrients(
                calcium_mg=5.0, iron_mg=0.3, magnesium_mg=59.0, potassium_mg=75.0, sodium_mg=1.0, zinc_mg=0.7
            ),
        },
        {
            "external_id": "TACO-003",
            "name": "Feijão, carioca, cozido",
            "category": "Cereais e leguminosas",
            "energy_kcal": 76.00,
            "protein_g": 4.80,
            "carbohydrate_g": 13.60,
            "lipid_g": 0.50,
            "fiber_g": 8.50,
            "micronutrients": build_micronutrients(
                calcium_mg=27.0, iron_mg=1.3, magnesium_mg=42.0, potassium_mg=255.0, sodium_mg=2.0, zinc_mg=0.7
            ),
        },
        {
            "external_id": "TACO-004",
            "name": "Feijão, preto, cozido",
            "category": "Cereais e leguminosas",
            "energy_kcal": 77.00,
            "protein_g": 4.50,
            "carbohydrate_g": 14.00,
            "lipid_g": 0.50,
            "fiber_g": 8.40,
            "micronutrients": build_micronutrients(
                calcium_mg=29.0, iron_mg=1.5, magnesium_mg=40.0, potassium_mg=256.0, sodium_mg=2.0, zinc_mg=0.7
            ),
        },
        # Carnes e Aves
        {
            "external_id": "TACO-005",
            "name": "Frango, peito, sem pele, grelhado",
            "category": "Carnes e derivados",
            "energy_kcal": 159.00,
            "protein_g": 32.00,
            "carbohydrate_g": 0.00,
            "lipid_g": 2.50,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=2.0, iron_mg=0.4, magnesium_mg=28.0, potassium_mg=386.0, sodium_mg=50.0, zinc_mg=1.1,
                vitamin_b12_mcg=0.3, cholesterol_mg=89.0
            ),
        },
        {
            "external_id": "TACO-006",
            "name": "Frango, peito, sem pele, cru",
            "category": "Carnes e derivados",
            "energy_kcal": 119.00,
            "protein_g": 21.50,
            "carbohydrate_g": 0.00,
            "lipid_g": 3.00,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=2.0, iron_mg=0.4, magnesium_mg=29.0, potassium_mg=370.0, sodium_mg=51.0, zinc_mg=0.8,
                vitamin_b12_mcg=0.3, cholesterol_mg=58.0
            ),
        },
        {
            "external_id": "TACO-007",
            "name": "Carne, bovina, patinho, sem gordura, grelhado",
            "category": "Carnes e derivados",
            "energy_kcal": 219.00,
            "protein_g": 35.90,
            "carbohydrate_g": 0.00,
            "lipid_g": 7.30,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=5.0, iron_mg=3.0, magnesium_mg=28.0, potassium_mg=383.0, sodium_mg=61.0, zinc_mg=5.3,
                vitamin_b12_mcg=2.4, cholesterol_mg=126.0
            ),
        },
        {
            "external_id": "TACO-008",
            "name": "Carne, bovina, patinho, sem gordura, cozido",
            "category": "Carnes e derivados",
            "energy_kcal": 219.00,
            "protein_g": 34.00,
            "carbohydrate_g": 0.00,
            "lipid_g": 8.00,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=4.0, iron_mg=2.8, magnesium_mg=27.0, potassium_mg=330.0, sodium_mg=65.0, zinc_mg=5.1,
                vitamin_b12_mcg=2.2, cholesterol_mg=115.0
            ),
        },
        {
            "external_id": "TACO-009",
            "name": "Carne, bovina, acém, moído, cozido",
            "category": "Carnes e derivados",
            "energy_kcal": 212.00,
            "protein_g": 26.70,
            "carbohydrate_g": 0.00,
            "lipid_g": 10.90,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=6.0, iron_mg=2.5, magnesium_mg=22.0, potassium_mg=300.0, sodium_mg=68.0, zinc_mg=4.8,
                vitamin_b12_mcg=2.1, cholesterol_mg=98.0
            ),
        },
        # Ovos
        {
            "external_id": "TACO-010",
            "name": "Ovo, de galinha, inteiro, cozido",
            "category": "Ovos e derivados",
            "energy_kcal": 146.00,
            "protein_g": 13.00,
            "carbohydrate_g": 0.60,
            "lipid_g": 9.50,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=49.0, iron_mg=1.5, magnesium_mg=13.0, potassium_mg=151.0, sodium_mg=146.0, zinc_mg=1.2,
                vitamin_b12_mcg=1.1, cholesterol_mg=356.0
            ),
        },
        {
            "external_id": "TACO-011",
            "name": "Ovo, de galinha, clara, cozida",
            "category": "Ovos e derivados",
            "energy_kcal": 54.00,
            "protein_g": 13.40,
            "carbohydrate_g": 0.00,
            "lipid_g": 0.10,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=6.0, iron_mg=0.1, magnesium_mg=11.0, potassium_mg=170.0, sodium_mg=168.0, zinc_mg=0.0,
                vitamin_b12_mcg=0.1, cholesterol_mg=0.0
            ),
        },
        # Tubérculos e Cereais
        {
            "external_id": "TACO-012",
            "name": "Aveia, flocos, crua",
            "category": "Cereais e leguminosas",
            "energy_kcal": 394.00,
            "protein_g": 13.90,
            "carbohydrate_g": 66.60,
            "lipid_g": 8.50,
            "fiber_g": 9.10,
            "micronutrients": build_micronutrients(
                calcium_mg=48.0, iron_mg=4.4, magnesium_mg=119.0, potassium_mg=340.0, sodium_mg=5.0, zinc_mg=2.6
            ),
        },
        {
            "external_id": "TACO-013",
            "name": "Farinha, de arroz",
            "category": "Cereais e leguminosas",
            "energy_kcal": 363.00,
            "protein_g": 7.30,
            "carbohydrate_g": 80.30,
            "lipid_g": 0.70,
            "fiber_g": 1.90,
            "micronutrients": build_micronutrients(
                calcium_mg=7.0, iron_mg=0.6, magnesium_mg=35.0, potassium_mg=110.0, sodium_mg=1.0, zinc_mg=1.1
            ),
        },
        {
            "external_id": "TACO-014",
            "name": "Tapioca, goma / massa fresca",
            "category": "Tubérculos e derivados",
            "energy_kcal": 242.00,
            "protein_g": 0.00,
            "carbohydrate_g": 60.00,
            "lipid_g": 0.00,
            "fiber_g": 0.20,
            "micronutrients": build_micronutrients(
                calcium_mg=10.0, iron_mg=0.2, magnesium_mg=3.0, potassium_mg=15.0, sodium_mg=1.0, zinc_mg=0.1
            ),
        },
        {
            "external_id": "TACO-015",
            "name": "Macarrão, trigo, cozido",
            "category": "Cereais e leguminosas",
            "energy_kcal": 102.00,
            "protein_g": 3.50,
            "carbohydrate_g": 21.00,
            "lipid_g": 0.30,
            "fiber_g": 1.10,
            "micronutrients": build_micronutrients(
                calcium_mg=7.0, iron_mg=0.4, magnesium_mg=15.0, potassium_mg=35.0, sodium_mg=1.0, zinc_mg=0.5
            ),
        },
        {
            "external_id": "TACO-016",
            "name": "Batata, doce, cozida",
            "category": "Tubérculos e derivados",
            "energy_kcal": 77.00,
            "protein_g": 0.60,
            "carbohydrate_g": 18.40,
            "lipid_g": 0.10,
            "fiber_g": 2.20,
            "micronutrients": build_micronutrients(
                calcium_mg=17.0, iron_mg=0.2, magnesium_mg=11.0, potassium_mg=204.0, sodium_mg=3.0, zinc_mg=0.1,
                vitamin_c_mg=23.8
            ),
        },
        {
            "external_id": "TACO-017",
            "name": "Batata, inglesa, cozida",
            "category": "Tubérculos e derivados",
            "energy_kcal": 52.00,
            "protein_g": 1.20,
            "carbohydrate_g": 11.90,
            "lipid_g": 0.00,
            "fiber_g": 1.30,
            "micronutrients": build_micronutrients(
                calcium_mg=4.0, iron_mg=0.4, magnesium_mg=15.0, potassium_mg=161.0, sodium_mg=2.0, zinc_mg=0.2,
                vitamin_c_mg=3.8
            ),
        },
        # Panificados
        {
            "external_id": "TACO-018",
            "name": "Pão, francês",
            "category": "Panificados e confeitaria",
            "energy_kcal": 300.00,
            "protein_g": 8.00,
            "carbohydrate_g": 58.60,
            "lipid_g": 3.10,
            "fiber_g": 2.30,
            "micronutrients": build_micronutrients(
                calcium_mg=16.0, iron_mg=1.0, magnesium_mg=25.0, potassium_mg=155.0, sodium_mg=648.0, zinc_mg=0.8
            ),
        },
        {
            "external_id": "TACO-019",
            "name": "Pão, de forma, tradicional",
            "category": "Panificados e confeitaria",
            "energy_kcal": 253.00,
            "protein_g": 9.40,
            "carbohydrate_g": 49.90,
            "lipid_g": 2.10,
            "fiber_g": 2.40,
            "micronutrients": build_micronutrients(
                calcium_mg=82.0, iron_mg=3.4, magnesium_mg=25.0, potassium_mg=120.0, sodium_mg=507.0, zinc_mg=0.7
            ),
        },
        # Frutas
        {
            "external_id": "TACO-020",
            "name": "Banana, prata, crua",
            "category": "Frutas e derivados",
            "energy_kcal": 98.00,
            "protein_g": 1.30,
            "carbohydrate_g": 26.00,
            "lipid_g": 0.10,
            "fiber_g": 2.00,
            "micronutrients": build_micronutrients(
                calcium_mg=8.0, iron_mg=0.4, magnesium_mg=26.0, potassium_mg=358.0, sodium_mg=1.0, zinc_mg=0.2,
                vitamin_c_mg=21.6
            ),
        },
        {
            "external_id": "TACO-021",
            "name": "Banana, nanica, crua",
            "category": "Frutas e derivados",
            "energy_kcal": 92.00,
            "protein_g": 1.40,
            "carbohydrate_g": 23.80,
            "lipid_g": 0.10,
            "fiber_g": 1.90,
            "micronutrients": build_micronutrients(
                calcium_mg=3.0, iron_mg=0.3, magnesium_mg=24.0, potassium_mg=376.0, sodium_mg=1.0, zinc_mg=0.2,
                vitamin_c_mg=5.9
            ),
        },
        {
            "external_id": "TACO-022",
            "name": "Maçã, fuji, com casca, fresca",
            "category": "Frutas e derivados",
            "energy_kcal": 56.00,
            "protein_g": 0.30,
            "carbohydrate_g": 15.20,
            "lipid_g": 0.00,
            "fiber_g": 1.30,
            "micronutrients": build_micronutrients(
                calcium_mg=2.0, iron_mg=0.1, magnesium_mg=2.0, potassium_mg=75.0, sodium_mg=1.0, zinc_mg=0.1
            ),
        },
        # Óleos e Gorduras
        {
            "external_id": "TACO-023",
            "name": "Azeite, de oliva, extravirgem",
            "category": "Óleos e gorduras",
            "energy_kcal": 884.00,
            "protein_g": 0.00,
            "carbohydrate_g": 0.00,
            "lipid_g": 100.00,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(),
        },
        {
            "external_id": "TACO-024",
            "name": "Pasta de amendoim, integral",
            "category": "Leguminosas e oleaginosas",
            "energy_kcal": 588.00,
            "protein_g": 25.00,
            "carbohydrate_g": 20.00,
            "lipid_g": 50.00,
            "fiber_g": 8.00,
            "micronutrients": build_micronutrients(
                calcium_mg=54.0, iron_mg=2.1, magnesium_mg=154.0, potassium_mg=649.0, sodium_mg=5.0, zinc_mg=3.1
            ),
        },
        # Laticínios
        {
            "external_id": "TACO-025",
            "name": "Queijo, muçarela",
            "category": "Laticínios",
            "energy_kcal": 330.00,
            "protein_g": 22.60,
            "carbohydrate_g": 3.00,
            "lipid_g": 25.20,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=875.0, iron_mg=0.5, magnesium_mg=24.0, potassium_mg=85.0, sodium_mg=580.0, zinc_mg=3.2,
                vitamin_b12_mcg=1.5, cholesterol_mg=85.0
            ),
        },
        {
            "external_id": "TACO-026",
            "name": "Leite, de vaca, pasteurizado, integral",
            "category": "Laticínios",
            "energy_kcal": 60.00,
            "protein_g": 3.10,
            "carbohydrate_g": 4.70,
            "lipid_g": 3.20,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=123.0, iron_mg=0.1, magnesium_mg=10.0, potassium_mg=142.0, sodium_mg=49.0, zinc_mg=0.4,
                vitamin_c_mg=1.0, vitamin_b12_mcg=0.4, cholesterol_mg=14.0
            ),
        },
        {
            "external_id": "TACO-027",
            "name": "Leite, de vaca, pasteurizado, desnatado",
            "category": "Laticínios",
            "energy_kcal": 35.00,
            "protein_g": 3.40,
            "carbohydrate_g": 4.90,
            "lipid_g": 0.10,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=134.0, iron_mg=0.1, magnesium_mg=11.0, potassium_mg=156.0, sodium_mg=52.0, zinc_mg=0.4,
                vitamin_c_mg=1.0, vitamin_b12_mcg=0.4, cholesterol_mg=2.0
            ),
        },
        # Pescados
        {
            "external_id": "TACO-028",
            "name": "Peixe, atum, conserva em água",
            "category": "Pescados e frutos do mar",
            "energy_kcal": 116.00,
            "protein_g": 26.00,
            "carbohydrate_g": 0.00,
            "lipid_g": 0.80,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=8.0, iron_mg=1.3, magnesium_mg=30.0, potassium_mg=280.0, sodium_mg=350.0, zinc_mg=0.8,
                vitamin_b12_mcg=2.9, cholesterol_mg=42.0
            ),
        },
        {
            "external_id": "TACO-029",
            "name": "Peixe, tilápia, filé, grelhado",
            "category": "Pescados e frutos do mar",
            "energy_kcal": 128.00,
            "protein_g": 26.20,
            "carbohydrate_g": 0.00,
            "lipid_g": 2.70,
            "fiber_g": 0.00,
            "micronutrients": build_micronutrients(
                calcium_mg=14.0, iron_mg=0.6, magnesium_mg=34.0, potassium_mg=380.0, sodium_mg=52.0, zinc_mg=0.5,
                vitamin_b12_mcg=1.9, cholesterol_mg=57.0
            ),
        },
    ]

    for item in staples:
        item["dominant_macro"] = classify_dominant_macro(
            item["protein_g"], item["carbohydrate_g"], item["lipid_g"]
        )
        item["source"] = "TACO"
        item["serving_size_g"] = 100.00

    return staples

# -----------------------------------------------------------------------------
# NÍVEL 1: INGESTÃO DE ARQUIVO LOCAL EM raw-data/
# -----------------------------------------------------------------------------
def load_local_file() -> Optional[List[Dict[str, Any]]]:
    """
    Verifica se existe arquivo JSON, CSV ou XLSX na pasta raw-data/ e processa.
    """
    if not os.path.exists(RAW_DATA_DIR):
        os.makedirs(RAW_DATA_DIR, exist_ok=True)
        return None

    json_files = [f for f in os.listdir(RAW_DATA_DIR) if f.endswith(".json")]
    if json_files:
        target_file = os.path.join(RAW_DATA_DIR, json_files[0])
        logger.info(f"Nível 1: Carregando arquivo JSON local '{target_file}'...")
        try:
            with open(target_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            return parse_json_to_foods(data)
        except Exception as e:
            logger.warning(f"Nível 1: Erro ao carregar JSON local: {e}")

    files = [f for f in os.listdir(RAW_DATA_DIR) if f.endswith((".csv", ".xlsx", ".xls"))]
    if not files:
        logger.info("Nível 1: Nenhum arquivo local encontrado em raw-data/.")
        return None

    target_file = os.path.join(RAW_DATA_DIR, sorted(files)[0])
    logger.info(f"Nível 1: Processando arquivo local '{target_file}'...")

    try:
        if target_file.endswith(".csv"):
            df = pd.read_csv(target_file, encoding="utf-8-sig", sep=None, engine="python")
        else:
            df = pd.read_excel(target_file)

        return parse_dataframe_to_foods(df)
    except Exception as e:
        logger.warning(f"Nível 1: Falha ao ler arquivo local '{target_file}': {e}. Acionando fallback.")
        return None

# -----------------------------------------------------------------------------
# NÍVEL 2: DOWNLOAD AUTOMATIZADO DE REPOSITÓRIO PÚBLICO ESTÁVEL
# -----------------------------------------------------------------------------
TACO_MIRROR_URLS = [
    # Mirror oficial padronizado da TACO 4ª edição em JSON (597 alimentos)
    "https://raw.githubusercontent.com/marcelosanto/tabela_taco/master/TACO.json",
]

def download_taco_dataset() -> Optional[List[Dict[str, Any]]]:
    """
    Tenta baixar os 597 alimentos da TACO 4ª Edição a partir de repositório público resiliente.
    """
    logger.info("Nível 2: Tentando download automatizado da Tabela TACO 4ª Edição...")
    os.makedirs(RAW_DATA_DIR, exist_ok=True)

    for url in TACO_MIRROR_URLS:
        try:
            logger.info(f"Nível 2: Baixando de {url}...")
            resp = requests.get(url, timeout=15)
            if resp.status_code == 200:
                data = resp.json()
                local_cache = os.path.join(RAW_DATA_DIR, "taco_raw.json")
                with open(local_cache, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
                logger.info(f"Nível 2: Download JSON concluído com sucesso ({len(data)} itens salvos em raw-data/taco_raw.json).")
                return parse_json_to_foods(data)
        except Exception as e:
            logger.warning(f"Nível 2: Erro ao baixar de {url}: {e}")

    logger.warning("Nível 2: Todos os downloads automatizados falharam ou timeout atingido.")
    return None

# -----------------------------------------------------------------------------
# PARSERS AUXILIARES (JSON / DATAFRAME)
# -----------------------------------------------------------------------------
def parse_json_to_foods(data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Converte estrutura JSON da TACO em lista de alimentos normalizada.
    """
    parsed_foods = []
    for idx, item in enumerate(data, start=1):
        name = item.get("description") or item.get("name") or item.get("alimento") or f"Alimento TACO #{idx}"
        
        # Preserva ID original da TACO
        ext_raw = item.get("id") or item.get("code")
        if ext_raw is not None and str(ext_raw).strip() != "":
            try:
                external_id = f"TACO-{int(clean_num(ext_raw)):03d}"
            except Exception:
                external_id = f"TACO-{idx:03d}"
        else:
            external_id = f"TACO-{idx:03d}"

        category = item.get("category") or "Geral"

        p = clean_num(item.get("protein_g") or item.get("protein") or item.get("proteina"))
        c = clean_num(item.get("carbohydrate_g") or item.get("carbohydrate") or item.get("carboidrato"))
        f = clean_num(item.get("lipid_g") or item.get("lipid") or item.get("lipideos") or item.get("gordura"))
        energy = clean_num(item.get("energy_kcal") or item.get("kcal") or item.get("energia"))

        # Auditoria Atwater se energy_kcal vier zerado ou inválido
        if energy <= 0.0 and (p > 0 or c > 0 or f > 0):
            energy = round(p * 4.0 + c * 4.0 + f * 9.0, 2)

        fiber = clean_num(item.get("fiber_g") or item.get("fiber") or item.get("fibra"))

        micros = build_micronutrients(
            calcium_mg=clean_num(item.get("calcium_mg") or item.get("calcium") or item.get("calcio")),
            iron_mg=clean_num(item.get("iron_mg") or item.get("iron") or item.get("ferro")),
            magnesium_mg=clean_num(item.get("magnesium_mg") or item.get("magnesium") or item.get("magnesio")),
            potassium_mg=clean_num(item.get("potassium_mg") or item.get("potassium") or item.get("potassio")),
            sodium_mg=clean_num(item.get("sodium_mg") or item.get("sodium") or item.get("sodio")),
            zinc_mg=clean_num(item.get("zinc_mg") or item.get("zinc") or item.get("zinco")),
            vitamin_c_mg=clean_num(item.get("vitaminC_mg") or item.get("vitamin_c_mg") or item.get("vitamin_c") or item.get("vitamina_c")),
            vitamin_b12_mcg=clean_num(item.get("vitamin_b12_mcg") or item.get("vitamin_b12") or item.get("vitamina_b12")),
            cholesterol_mg=clean_num(item.get("cholesterol_mg") or item.get("cholesterol") or item.get("colesterol")),
        )

        dominant_macro = classify_dominant_macro(p, c, f)

        parsed_foods.append({
            "external_id": external_id,
            "name": name.strip(),
            "source": "TACO",
            "category": category.strip(),
            "dominant_macro": dominant_macro,
            "serving_size_g": 100.00,
            "energy_kcal": energy,
            "protein_g": p,
            "carbohydrate_g": c,
            "lipid_g": f,
            "fiber_g": fiber,
            "micronutrients": micros,
        })

    return parsed_foods

def parse_dataframe_to_foods(df: pd.DataFrame) -> List[Dict[str, Any]]:
    """
    Normaliza DataFrame pandas de CSV ou XLSX para o contrato de alimentos.
    """
    col_map = {}
    for col in df.columns:
        c_norm = col.lower().strip()
        if "alimento" in c_norm or "descri" in c_norm or "name" in c_norm:
            col_map["name"] = col
        elif "código" in c_norm or "codigo" in c_norm or "num" in c_norm or "id" in c_norm:
            col_map["id"] = col
        elif "categoria" in c_norm or "category" in c_norm:
            col_map["category"] = col
        elif "prote" in c_norm:
            col_map["protein"] = col
        elif "carbo" in c_norm:
            col_map["carb"] = col
        elif "lipíd" in c_norm or "lipid" in c_norm or "gord" in c_norm:
            col_map["lipid"] = col
        elif "kcal" in c_norm or "energia" in c_norm or "energy" in c_norm:
            col_map["energy"] = col
        elif "fibra" in c_norm or "fiber" in c_norm:
            col_map["fiber"] = col
        elif "cálcio" in c_norm or "calcio" in c_norm or "calcium" in c_norm:
            col_map["calcium"] = col
        elif "ferro" in c_norm or "iron" in c_norm:
            col_map["iron"] = col
        elif "magné" in c_norm or "magne" in c_norm or "magnesium" in c_norm:
            col_map["magnesium"] = col
        elif "potás" in c_norm or "potas" in c_norm or "potassium" in c_norm:
            col_map["potassium"] = col
        elif "sódio" in c_norm or "sodio" in c_norm or "sodium" in c_norm:
            col_map["sodium"] = col
        elif "zinco" in c_norm or "zinc" in c_norm:
            col_map["zinc"] = col
        elif "vitamina c" in c_norm or "vitamin c" in c_norm:
            col_map["vitamin_c"] = col
        elif "vitamina b12" in c_norm or "vitamin b12" in c_norm:
            col_map["vitamin_b12"] = col
        elif "colesterol" in c_norm or "cholesterol" in c_norm:
            col_map["cholesterol"] = col

    parsed = []
    for idx, row in df.iterrows():
        name = str(row[col_map["name"]]).strip() if "name" in col_map else f"Alimento TACO #{idx + 1}"
        ext_raw = row[col_map["id"]] if "id" in col_map else idx + 1
        try:
            ext_id = f"TACO-{int(clean_num(ext_raw)):03d}"
        except Exception:
            ext_id = f"TACO-{idx + 1:03d}"

        category = str(row[col_map["category"]]).strip() if "category" in col_map else "Geral"

        p = clean_num(row[col_map["protein"]]) if "protein" in col_map else 0.0
        c = clean_num(row[col_map["carb"]]) if "carb" in col_map else 0.0
        f = clean_num(row[col_map["lipid"]]) if "lipid" in col_map else 0.0
        energy = clean_num(row[col_map["energy"]]) if "energy" in col_map else 0.0
        if energy <= 0.0 and (p > 0 or c > 0 or f > 0):
            energy = round(p * 4.0 + c * 4.0 + f * 9.0, 2)
        fiber = clean_num(row[col_map["fiber"]]) if "fiber" in col_map else 0.0

        micros = build_micronutrients(
            calcium_mg=clean_num(row[col_map["calcium"]]) if "calcium" in col_map else 0.0,
            iron_mg=clean_num(row[col_map["iron"]]) if "iron" in col_map else 0.0,
            magnesium_mg=clean_num(row[col_map["magnesium"]]) if "magnesium" in col_map else 0.0,
            potassium_mg=clean_num(row[col_map["potassium"]]) if "potassium" in col_map else 0.0,
            sodium_mg=clean_num(row[col_map["sodium"]]) if "sodium" in col_map else 0.0,
            zinc_mg=clean_num(row[col_map["zinc"]]) if "zinc" in col_map else 0.0,
            vitamin_c_mg=clean_num(row[col_map["vitamin_c"]]) if "vitamin_c" in col_map else 0.0,
            vitamin_b12_mcg=clean_num(row[col_map["vitamin_b12"]]) if "vitamin_b12" in col_map else 0.0,
            cholesterol_mg=clean_num(row[col_map["cholesterol"]]) if "cholesterol" in col_map else 0.0,
        )

        dominant_macro = classify_dominant_macro(p, c, f)

        parsed.append({
            "external_id": ext_id,
            "name": name,
            "source": "TACO",
            "category": category,
            "dominant_macro": dominant_macro,
            "serving_size_g": 100.00,
            "energy_kcal": energy,
            "protein_g": p,
            "carbohydrate_g": c,
            "lipid_g": f,
            "fiber_g": fiber,
            "micronutrients": micros,
        })

    return parsed

# -----------------------------------------------------------------------------
# PERSISTÊNCIA: UPSERT EM LOTE NO POSTGRESQL (IDEMPOTÊNCIA ABSOLUTA)
# -----------------------------------------------------------------------------
UPSERT_FOOD_SQL = text("""
INSERT INTO foods (
    name,
    source,
    external_id,
    category,
    dominant_macro,
    serving_size_g,
    energy_kcal,
    protein_g,
    carbohydrate_g,
    lipid_g,
    fiber_g,
    micronutrients,
    updated_at
) VALUES (
    :name,
    :source,
    :external_id,
    :category,
    :dominant_macro,
    :serving_size_g,
    :energy_kcal,
    :protein_g,
    :carbohydrate_g,
    :lipid_g,
    :fiber_g,
    CAST(:micronutrients AS jsonb),
    CURRENT_TIMESTAMP
)
ON CONFLICT (source, external_id) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    dominant_macro = EXCLUDED.dominant_macro,
    serving_size_g = EXCLUDED.serving_size_g,
    energy_kcal = EXCLUDED.energy_kcal,
    protein_g = EXCLUDED.protein_g,
    carbohydrate_g = EXCLUDED.carbohydrate_g,
    lipid_g = EXCLUDED.lipid_g,
    fiber_g = EXCLUDED.fiber_g,
    micronutrients = EXCLUDED.micronutrients,
    updated_at = CURRENT_TIMESTAMP;
""")

def execute_batch_upsert(engine: Engine, foods: List[Dict[str, Any]], batch_size: int = 100) -> int:
    """
    Executa a carga idempotente em lote utilizando transações seguras.
    """
    total_inserted = 0
    with engine.begin() as conn:
        for i in range(0, len(foods), batch_size):
            batch = foods[i : i + batch_size]
            params = []
            for item in batch:
                params.append({
                    "name": item["name"],
                    "source": item["source"],
                    "external_id": item["external_id"],
                    "category": item["category"],
                    "dominant_macro": item["dominant_macro"],
                    "serving_size_g": item["serving_size_g"],
                    "energy_kcal": item["energy_kcal"],
                    "protein_g": item["protein_g"],
                    "carbohydrate_g": item["carbohydrate_g"],
                    "lipid_g": item["lipid_g"],
                    "fiber_g": item["fiber_g"],
                    "micronutrients": json.dumps(item["micronutrients"]),
                })
            conn.execute(UPSERT_FOOD_SQL, params)
            total_inserted += len(batch)
            logger.info(f"Progresso: {total_inserted}/{len(foods)} alimentos processados no PostgreSQL.")

    return total_inserted

# -----------------------------------------------------------------------------
# AUDITORIA E RELATÓRIO PÓS-CARGA
# -----------------------------------------------------------------------------
def run_post_load_audit(engine: Engine):
    """
    Executa queries analíticas para comprovação cirúrgica da integridade dos dados no banco.
    """
    with engine.connect() as conn:
        # Total de alimentos
        total_foods = conn.execute(text("SELECT COUNT(*) FROM foods WHERE source = 'TACO';")).scalar()
        logger.info(f"📊 [Auditoria] Total de alimentos TACO cadastrados: {total_foods}")

        # Distribuição de macro dominante
        macro_dist = conn.execute(
            text("SELECT dominant_macro, COUNT(*) FROM foods WHERE source = 'TACO' GROUP BY dominant_macro ORDER BY COUNT(*) DESC;")
        ).fetchall()
        logger.info("📊 [Auditoria] Distribuição por Macro Dominante:")
        for macro, count in macro_dist:
            logger.info(f"   • {macro:<10}: {count:>4} alimentos")

        # Amostragem de alimentos básicos verificados
        logger.info("🔍 [Auditoria] Amostra de Alimentos Básicos (Staples) no PostgreSQL:")
        sample_query = text("""
            SELECT name, dominant_macro, energy_kcal, protein_g, carbohydrate_g, lipid_g, fiber_g,
                   micronutrients->>'calcium_mg' as calcium,
                   micronutrients->>'sodium_mg' as sodium,
                   micronutrients->>'potassium_mg' as potassium
            FROM foods
            WHERE source = 'TACO' AND external_id IN ('TACO-001', 'TACO-003', 'TACO-005', 'TACO-010', 'TACO-023')
            ORDER BY external_id;
        """)
        samples = conn.execute(sample_query).fetchall()
        for s in samples:
            logger.info(
                f"   🍲 {s[0]} | {s[1]} | {s[2]} kcal | P: {s[3]}g | C: {s[4]}g | G: {s[5]}g | Fibra: {s[6]}g | Na: {s[8]}mg | K: {s[9]}mg"
            )

# -----------------------------------------------------------------------------
# FLUXO PRINCIPAL (ORQUESTRADOR DE 3 NÍVEIS)
# -----------------------------------------------------------------------------
def run_pipeline():
    logger.info("=================================================================")
    logger.info("INICIANDO PIPELINE DE ENGENHARIA DE DADOS: TABELA TACO 4ª EDIÇÃO")
    logger.info("=================================================================")

    # Teste de conexão com o Banco
    logger.info(f"Conectando ao PostgreSQL em {DB_HOST}:{DB_PORT}/{DB_NAME}...")
    try:
        engine = create_engine(DATABASE_URI, pool_pre_ping=True)
        with engine.connect() as conn:
            conn.execute(text("SELECT 1;"))
        logger.info("Conexão com PostgreSQL estabelecida com sucesso!")
    except Exception as e:
        logger.error(f"Erro fatal ao conectar no banco PostgreSQL: {e}")
        sys.exit(1)

    # Execução em 3 Níveis
    all_foods_dict: Dict[str, Dict[str, Any]] = {}

    # Nível 1: Arquivo Local
    local_foods = load_local_file()
    if local_foods:
        for f in local_foods:
            all_foods_dict[f["external_id"]] = f
        logger.info(f"Nível 1 concluído: {len(local_foods)} alimentos carregados de arquivo local.")

    # Nível 2: Download Automatizado (se necessário ou para enriquecer)
    if not local_foods or len(local_foods) < 500:
        downloaded_foods = download_taco_dataset()
        if downloaded_foods:
            for f in downloaded_foods:
                all_foods_dict[f["external_id"]] = f
            logger.info(f"Nível 2 concluído: {len(downloaded_foods)} alimentos carregados via mirror público.")

    # Nível 3: Garantia da Base Curada de Staple Foods (Mandatório)
    logger.info("Nível 3: Injetando/Validando Base Curada de 29 Alimentos Básicos da Dieta Brasileira...")
    staples = get_staple_foods_seed()
    for s in staples:
        all_foods_dict[s["external_id"]] = s

    final_foods_list = list(all_foods_dict.values())
    logger.info(f"Total consolidado para persistência: {len(final_foods_list)} alimentos.")

    # Executa Upsert Idempotente
    logger.info("Iniciando persistência com Upsert em Lote no PostgreSQL...")
    inserted_count = execute_batch_upsert(engine, final_foods_list)
    logger.info(f"✅ Upsert finalizado: {inserted_count} alimentos processados.")

    # Auditoria e Validação
    run_post_load_audit(engine)

    logger.info("=================================================================")
    logger.info("PIPELINE ETL TACO CONCLUÍDO COM SUCESSO E RIGOR NUMÉRICO MÁXIMO")
    logger.info("=================================================================")

if __name__ == "__main__":
    run_pipeline()
