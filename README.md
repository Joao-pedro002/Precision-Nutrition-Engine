# 🥗 Precision Nutrition Engine

Sistema de rastreamento nutricional de alta precisão orientado a dados. Desenvolvido com Back-end em Java 21 (Spring Boot), Front-end PWA em Next.js (React Server Components) e pipeline ETL em Python para ingestão das bases de dados TACO e USDA.

## 🎯 Visão Geral
Projetado para superar as limitações de aplicativos comerciais comuns, o sistema implementa um motor determinístico em `BigDecimal` que calcula substituições alimentares equivalentes em tempo real, detectando e alertando proativamente sobre o **Calorie Drift** (desvios calóricos ocultos causados por macros secundários).

## 🏗️ Arquitetura e Tech Stack
* **Engenharia de Dados (Python/Pandas):** Pipeline ETL resiliente para higienização da Tabela TACO (4ª Edição), aplicando *clamping* de resíduos físico-químicos e normalização de atributos.
* **Core Back-end (Java 21 / Spring Boot 3):** API REST escalável utilizando *Virtual Threads*, com integração sob demanda à API USDA FoodData Central (padrão *Read-Through Cache*).
* **Persistência Híbrida (PostgreSQL 16):** Modelagem avançada combinando colunas relacionais indexadas com matrizes de micronutrientes em `JSONB`, aceleradas por índices de similaridade trigramática (`pg_trgm`) para buscas textuais em sub-2ms.
* **Front-end PWA (Next.js 14):** Interface responsiva estruturada em *React Server Components* e *Optimistic UI*, garantindo interações de dashboard e hidratação sem latência perceptível.

## 📂 Documentação e ADRs
Todas as decisões arquiteturais (Architecture Decision Records), o esquema da base de dados e o motor matemático encontram-se documentados e versionados na pasta `/docs/obsidian-vault`.