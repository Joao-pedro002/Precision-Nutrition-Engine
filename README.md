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

---

## 🚀 Guia Rápido de Inicialização (Quickstart)

Siga os passos abaixo para executar a aplicação completa em ambiente local.

### 📋 Pré-requisitos
* [Git](https://git-scm.com/)
* [Docker & Docker Compose](https://www.docker.com/)
* [JDK 21](https://adoptium.net/) e [Apache Maven 3.9+](https://maven.apache.org/)
* [Node.js 18+](https://nodejs.org/) e npm
* [Python 3.10+](https://www.python.org/) *(necessário apenas para a carga inicial da base TACO)*

---

### 1. Clonar o Repositório
```bash
git clone https://github.com/Joao-pedro002/Precision-Nutrition-Engine.git
cd Precision-Nutrition-Engine
```

---

### 2. Configuração de Ambiente & Proteção de Dados 🔒
> **Aviso de Privacidade e Segurança:** Nunca versione arquivos `.env` ou credenciais privadas no repositório. O arquivo `.gitignore` do projeto já protege arquivos sensíveis automaticamente.

Copie o template de exemplo para criar seu arquivo de variáveis local:
```bash
cp .env.example .env
```

* **Banco de Dados:** O sistema vem pré-configurado para conectar ao PostgreSQL local na porta `5433` (isolada para evitar conflitos com instâncias locais nativas na porta 5432).
* **Chave da API USDA (Opcional):** O sistema utiliza a `DEMO_KEY` pública por padrão. Caso deseje um limite maior de requisições, gere uma chave gratuita no portal [USDA FoodData Central](https://fdc.nal.usda.gov/api-key-signup.html) e defina em seu `.env`:
  ```env
  USDA_API_KEY=sua_chave_privada_aqui
  ```

---

### 3. Iniciar o Banco de Dados (PostgreSQL 16)
Na raiz do projeto, inicie o container com as extensões (`pg_trgm`, `unaccent`, `uuid-ossp`) e o schema inicial:
```bash
docker compose up -d
```
Verifique se o banco está pronto:
```bash
docker ps --filter "name=precision-postgres"
```

---

### 4. Carga dos Alimentos da TACO (ETL Python)
Execute o script de ingestão para popular a tabela com os 597 alimentos da TACO 4ª edição:
```bash
cd etl-python

# Criar e ativar o ambiente virtual
python -m venv .venv

# No Windows:
.venv\Scripts\activate
# No Linux/macOS:
# source .venv/bin/activate

# Instalar dependências e executar o ETL
pip install -r requirements.txt
python etl_taco.py

cd ..
```

---

### 5. Iniciar o Back-end (Spring Boot 3 / Java 21)
Abra um terminal e gere o pacote executável (ou execute diretamente via `java -jar` para máxima compatibilidade em Windows):
```bash
cd backend-spring
mvn package -DskipTests
java -jar target/backend-spring-1.0.0-SNAPSHOT.jar
```
> **Dica para Windows:** Executar via `java -jar` é a forma recomendada em ambientes Windows, pois encapsula todas as dependências no Fat JAR e evita limitações de tamanho de linha de comando ou incompatibilidades de codificação (acentos em nomes de usuário como `C:\Users\João`). Alternativamente, também é possível utilizar `mvn spring-boot:run`.
*(O banco executará as migrações Flyway e populará o protocolo Cutting de 2100 kcal e as 5 refeições padrão).*

---

### 6. Iniciar o Front-end (Next.js 14 PWA)
Abra outro terminal e inicie a interface do usuário na porta `3000`:
```bash
cd frontend-nextjs
npm install
npm run dev
```

---

### 7. Acessar o Aplicativo
Abra seu navegador em:
👉 **[http://localhost:3000](http://localhost:3000)**

* Documentação da API REST (Back-end): `http://localhost:8080/api/v1/dashboard`
* Testes do Back-end: `cd backend-spring && mvn test`
* Build de Produção do Front-end: `cd frontend-nextjs && npm run build`
