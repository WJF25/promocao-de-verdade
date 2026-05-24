# Estratégia de Persistência Idempotente (O Padrão Upsert)

**Autores:** Waldiney
**Revisores:** Bruno Ferraz
**Status:** Implementado
**Data:** Maio/2026

## 1. Contexto e Necessidade
*Qual é o problema que estamos tentando resolver?*
O sistema "Preço Histórico" monitora e salva o log diário de preços de produtos de e-commerce brasileiro através de rotinas automatizadas (jobs) e inserções manuais. Originalmente, o banco de dados impunha uma restrição rígida de unicidade (`UNIQUE(user_id, product_name, site_name, captured_at)`) na tabela `price_logs`.

Isso significava que, se houvesse uma mudança de preço ao longo do dia, ou se o usuário precisasse corrigir um erro de coleta, o banco rejeitava a operação com uma exceção de conflito de duplicidade. A correção exigia operações perigosas de exclusão seguida de inserção, o que aumentava a complexidade e o risco de falhas no código.

## 2. Objetivos e Não-Objetivos
*O que define o sucesso e o que está fora de escopo?*
* **Objetivos:** Permitir a inserção e atualização atômica de preços para um mesmo produto no mesmo dia. A tabela `price_logs` deve sempre refletir, de forma simples, o último estado conhecido do preço diário de um produto, simplificando a lógica da aplicação ao não exigir tratamento de erros try/catch nas rotas e jobs.
* **Não-Objetivos:** Não é objetivo desta tabela rastrear a linha do tempo exata das flutuações de preço intraday (hora a hora). A responsabilidade pesada de analisar o comportamento dos preços ao longo do tempo (ex: identificar promoções falsas comparando oscilações do `price_original` e `price_cash`) ficará a cargo de features e tabelas futuras, que consumirão os dados de log como matéria-prima.

## 3. Soluções Propostas e Trade-offs
*Quais caminhos podemos seguir e por que escolhemos este?*

### 3.1. Solução A: Upsert Nativo no PostgreSQL (Implementada)
Modificação da instrução `insert-price-log!` no HugSQL para utilizar a cláusula `ON CONFLICT (...) DO UPDATE`.

### 3.2. Solução B: Exclusão e Inserção Controlada pela Aplicação
A aplicação Clojure lidaria com o erro de Unique Constraint disparando uma query `DELETE` e tentando salvar novamente.

### 3.3. Comparativo e Justificativa (Por que A e não B?)
* **Vantagens da Solução A:** A resolução de conflitos ocorre no nível do motor relacional, tornando a persistência idempotente e atômica. O código Clojure fica mais coeso, focando em validações de negócio em vez de se defender contra restrições de banco.
* **Desvantagens da Solução A:** A aparente "perda" do valor inicial registrado pela manhã ao realizar uma atualização à tarde. Contudo, para o "coração" do sistema (descobrir se um desconto é real ou maquiado), a última aferição válida do dia na tabela `price_logs` é o dado que importa. As futuras análises comparativas gerarão seus próprios registros independentes toda vez que forem ativadas, mantendo o histórico de análise isolado da coleta bruta.
* **Por que a Solução B foi descartada:** Exigiria a implementação de transações de banco de dados na aplicação. Caso houvesse uma falha de rede exatamente após a deleção, o preço do dia seria perdido permanentemente.

## 4. Design Detalhado
Detalhes técnicos da solução escolhida:
* **Modelagem de dados (Contrato do Banco de Dados):** A restrição `UNIQUE(user_id, product_name, site_name, captured_at)` na tabela `price_logs` deixa de ser um bloqueio e passa a ser a chave de gatilho que indica ao PostgreSQL quais linhas devem sofrer sobrescrita.
* **Camada de Aplicação:** Os Controllers da API e as funções de processamento em lote (como `run-update-prices-job`) delegam a escrita com total confiança ao banco de dados, assumindo que a operação sempre refletirá o estado mais atualizado do dia.

## 5. Registro de Decisões (Decision Log)
*Espaço para pequenas decisões tomadas ao longo do refinamento.*

| Data | Decisão | Contexto / Justificativa | Status |
| :--- | :--- | :--- | :--- |
| 05/05 | Uso do padrão Upsert via HugSQL | Evitar concorrência e garantir atomicidade direto no PostgreSQL | Implementado |

## 6. Considerações Operacionais e de Escalabilidade
* **Plano de Rollout e Rollback:** *[Não fornecido no texto original - preencher se necessário]*
* **Observabilidade:** *[Não fornecido no texto original - preencher se necessário]*
* **Segurança:** *[Não fornecido no texto original - preencher se necessário]*
* **Escalabilidade:** Ao delegar a lógica de concorrência e atualização para o PostgreSQL, removemos viagens desnecessárias pela rede (roundtrips de leitura seguida de escrita), fator essencial para manter a resiliência e a velocidade do robô durante varreduras em massa.
