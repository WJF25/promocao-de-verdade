# Introdução ao Preço Histórico

Bem-vindo à documentação oficial do **Preço Histórico**, uma plataforma desenvolvida em Clojure, projetada para o monitoramento e *scraping* em massa de preços em e-commerces brasileiros.

Este diretório contém os registros de decisões arquiteturais (Design Docs) que guiam o desenvolvimento do projeto. O objetivo desta documentação é consolidar o conhecimento e guiar o time técnico sobre os *trade-offs*, padrões e contratos estabelecidos ao longo da evolução da plataforma.

## Índice de Design Docs

Abaixo está o resumo das principais decisões arquiteturais documentadas na pasta `doc/`. Clique nos links para acessar os documentos completos.

### 1. [Arquitetura Multi-Entry e Resiliência em Batch](./Arquitetura%20Multi-Entry%20e%20Resiliência%20em%20Batch.md)
Detalha como separamos o tráfego da API web da execução pesada dos robôs de *web scraping*. A arquitetura utiliza a biblioteca Chime para agendamento assíncrono noturno e implementa um sistema de *Jitter dinâmico* para emular o comportamento humano, mitigando o risco de bloqueios de IP nos e-commerces monitorados.

### 2. [Evolução do Contrato de API](./Evolução%20do%20Contrato%20de%20API.md)
Explica a transição da aplicação para uma estratégia de coleta de preços híbrida, aceitando tanto varreduras automáticas (robô) quanto inserções manuais. O documento destaca o uso engenhoso da biblioteca Malli (`{:closed true}`) não apenas como validador de schemas, mas como um orquestrador lógico para roteamento de fluxo.

### 3. [Persistência Idempotente (O Padrão Upsert)](./Persistencia_idempotente.md)
Documenta a abordagem utilizada no banco de dados para lidar com atualizações de preços de um mesmo produto no mesmo dia. Através da cláusula `ON CONFLICT (...) DO UPDATE` nativa do PostgreSQL via HugSQL, o sistema garante atualizações atômicas, evita colisões de chaves únicas e simplifica a camada de aplicação.

### 4. [Blindagem e Segurança por Design](./Blindagem%20e%20Segurança%20por%20Design.md)
Cobre as estratégias de autenticação e validação criadas para proteger a API sem penalizar o I/O do banco de dados. Detalha a adoção de JWT Stateless, hashing de senhas robusto com `buddy` (Bcrypt) e o isolamento contra payloads maliciosos, sustentados por uma cobertura de testes superior a 95%.

---

*Dica: Ao criar um novo Design Doc, lembre-se de utilizar o template padrão adotado pela equipe e adicionar o link com o resumo correspondente nesta página.*
