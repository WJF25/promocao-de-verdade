# Evolução do Contrato de API (Coleta Híbrida de Preços)

**Autores:** Waldiney
**Revisores:** Bruno Ferraz
**Status:** Implementado
**Data:** Maio/2026

## 1. Contexto e Necessidade
*Qual é o problema que estamos tentando resolver?*
A aplicação originalmente exigia apenas uma URL no payload da rota de criação de histórico de preços, delegando a responsabilidade de extração integralmente ao motor de *web scraping* interno. Com o aumento das defesas anti-bot em grandes e-commerces (como Mercado Livre e Magazine Luiza), o sistema precisou evoluir para aceitar inserções manuais de preços.

O desafio arquitetural era permitir essa nova funcionalidade sem criar "portas dos fundos" de desenvolvimento, sem duplicar rotas e mantendo a segurança estrita de validação do payload para evitar a entrada de dados sujos ou maliciosos.

## 2. Objetivos e Não-Objetivos
*O que define o sucesso e o que está fora de escopo?*
* **Objetivos:**
  * Permitir que a mesma rota HTTP (`POST /api/prices`) suporte a coleta automatizada (Robô) e a inserção declarada (Manual).
  * Manter a blindagem da API utilizando a validação de schemas fechados (`{:closed true}`) do Malli para rejeitar chaves não mapeadas.
* **Não-Objetivos:**
  * Não é objetivo criar lógicas de detecção de anomalias ou discrepâncias nos dados inseridos manualmente nesta camada. A API confia na validação estrutural do contrato.

## 3. Soluções Propostas e Trade-offs
*Quais caminhos podemos seguir e por que escolhemos este?*

### 3.1. Solução A: Malli como Orquestrador Lógico (Implementada)
Utilização do `malli.util` para criar composição de schemas. Criamos um schema flexível de entrada (`IncomingPayloadSchema`) que torna os dados de preço opcionais, e mantivemos o schema estrito (`PriceLogSchema`) para validar o dado manual completo antes de enviá-lo ao banco. O handler detecta a presença da chave `:price_cash` no payload para rotear o fluxo interno.

### 3.2. Solução B: Criação de um Endpoint Exclusivo (Ex: /api/prices/manual)
Divisão física do contrato da API, criando uma rota para o scraper e outra para a inserção manual.

### 3.3. Comparativo e Justificativa (Por que A e não B?)
* **Vantagens da Solução A:** Reaproveitamento da mesma rota e contrato. Não expõe rotas extras na superfície de ataque da API. Delega a decisão de fluxo para a estrutura de dados (uma abordagem muito idiomática em Clojure).
* **Desvantagens da Solução A:** O handler passa a ter um leve acoplamento à estrutura do payload para tomar decisões de roteamento (se a chave `:price_cash` existe, não chama o scraper). O benefício de manter a simplicidade da API e a integridade do banco superou o custo dessa ramificação de fluxo.
* **Por que a Solução B foi descartada:** Aumentaria a superfície da API e a manutenção de testes. A inserção manual é parte do domínio principal de "salvar histórico de preços", sendo mais coeso tratar a diferença apenas como fontes distintas do mesmo dado.

## 4. Design Detalhado
Detalhes técnicos da solução escolhida:
* **Modelagem de dados (Contrato da API):** O `PriceLogSchema` central é definido com `{:closed true}`, contendo chaves como `:product_name`, `:site_name` e `:price_cash`. A partir dele, o `IncomingPayloadSchema` foi derivado usando `mu/optional-keys`, exigindo estritamente apenas a `:url` para solicitações que dependem do robô.
* **Orquestração no Roteamento:** Na função `save-price-log-handler`, a requisição é validada de forma genérica. Se passar, uma cláusula `cond` verifica `(:price_cash body)`. Se verdadeiro, os dados são revalidados contra o schema estrito e persistidos no banco (fluxo salva-manualmente). Se falso, o fluxo aciona a função `scraper/fetch-product-data`.

## 5. Registro de Decisões (Decision Log)
*Espaço para pequenas decisões tomadas ao longo do refinamento.*

| Data | Decisão | Contexto / Justificativa | Status |
| :--- | :--- | :--- | :--- |
| 15/05 | Uso de `{:closed true}` no Malli | Garantir que payloads com propriedades maliciosas ou inesperadas sejam rejeitados de imediato | Implementado |
| 16/05 | Derivação via `mu/optional-keys` | Reaproveitar o schema principal para o payload de entrada, evitando duplicidade de código | Implementado |

## 6. Considerações Operacionais e de Escalabilidade
* **Plano de Rollout e Rollback:** *[Não fornecido no texto original - preencher se necessário]*
* **Observabilidade:** Diferenciar nos logs o volume de requisições que seguiram pelo fluxo manual vs. automatizado para entender o comportamento de uso.
* **Segurança:** A estratégia de composição de schemas protegeu o banco de dados contra inserções de tipos errôneos, enquanto manteve a flexibilidade necessária para contornar os bloqueios das lojas.
* **Escalabilidade:** O desenho absorve bem futuras melhorias. Como a chave base de orquestração é o payload, integrações com sistemas terceiros ou de lote que eventualmente já enviem o JSON completo não acionarão gargalos de *scraping*, caindo no fluxo de processamento rápido.