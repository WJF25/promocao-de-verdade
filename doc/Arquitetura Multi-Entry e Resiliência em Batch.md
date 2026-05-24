# Arquitetura Multi-Entry e Resiliência em Batch

**Autores:** Waldiney

**Revisores:** Bruno Ferraz

**Status:** Implementado

**Data:** Maio/2026

## 1. Contexto e Necessidade
*Qual é o problema que estamos tentando resolver?*
A plataforma precisa executar varreduras em massa (*web scraping*) nos e-commerces para atualizar o histórico diário de preços dos produtos monitorados. O *web scraping* é uma operação suscetível a instabilidades de rede e bloqueios. 

Se essa rotina rodasse acoplada às requisições do servidor HTTP da API (gerenciado pelo Jetty), falhas de *timeout* ou lentidão nas lojas poderiam exaurir as *threads* do servidor e derrubar a API para os usuários finais. Além disso, o envio sucessivo e sem pausas de requisições dispararia os mecanismos anti-bot dos sites, resultando no bloqueio do IP da aplicação.

## 2. Objetivos e Não-Objetivos
*O que define o sucesso e o que está fora de escopo?*
* **Objetivos:**
  * Isolar a execução dos fluxos automatizados (Jobs) do tráfego do servidor Web.
  * Garantir a resiliência da extração: uma falha pontual em uma URL ou um erro do banco de dados não pode derrubar a execução do lote inteiro.
  * Emular o comportamento humano adicionando intervalos de espera dinâmicos entre as requisições para mitigar banimentos.
* **Não-Objetivos:**
  * Otimizar o tempo de execução utilizando paralelismo extremo neste momento. O foco atual é a estabilidade e a evasão de bloqueios, e não a conclusão instantânea da fila.

## 3. Soluções Propostas e Trade-offs
*Quais caminhos podemos seguir e por que escolhemos este?*

### 3.1. Solução A: Arquitetura Multi-Entry com Jitter Dinâmico (Implementada)
Criação de pontos de entrada separados para a aplicação. O servidor web sobe via `core.clj` e orquestra o scheduler internamente. Em paralelo, o Job pode ser disparado de forma autônoma via linha de comando no `jobs_update_prices.clj` (usando a função `-main`).

### 3.2. Solução B: Rota HTTP Acionada por Cron Externo
Criação de um endpoint HTTP (ex: `/api/jobs/run`) chamado por um agendador externo do sistema operacional para varrer os links.

### 3.3. Comparativo e Justificativa (Por que A e não B?)
* **Vantagens da Solução A:** O ciclo de vida da API e do script de carga operam de forma isolada, não competindo diretamente da mesma forma pelas chamadas HTTP de entrada. O tratamento de erros engole falhas específicas, isolando o dano.
* **Desvantagens da Solução A:** A aplicação itera sequencialmente pelos produtos chamando a função `put-to-sleep-seconds`, o que atrasa cada chamada HTTP artificialmente. Assumimos o custo de um processamento demorado (linear em relação ao número de produtos) em troca de camuflar as requisições automatizadas.
* **Por que a Solução B foi descartada:** Requisições HTTP têm limites naturais de tempo e não devem ser mantidas abertas por horas enquanto o processamento em lote ocorre. Isso poderia levar a *timeouts* no lado do servidor web ou *leaks* de memória caso a conexão fosse cortada pelo cliente externo.

## 4. Design Detalhado
Detalhes técnicos da solução escolhida:
* **Orquestração Multi-Entry:** A aplicação possui seu fluxo primário via `core.clj`, que inicializa a base de dados e chama `server/start-server!`. Simultaneamente, é possível usar ferramentas de CLI para executar apenas o `jobs/-main`, que varre todo o banco atualizando preços e depois encerra o processo (`shutdown-agents`).
* **O Agendador Assíncrono (Chime):** O arquivo `jobs_update_prices.clj` inclui a função `start-scheduler!`, que configura a biblioteca `chime` para rodar o robô automaticamente a partir das 03:00 da madrugada, sob o fuso horário `America/Sao_Paulo`. Este scheduler conta com um `error-handler` nativo que previne o travamento da thread caso ocorra um pânico durante a execução noturna.
* **Pausa Randômica (Jitter Dinâmico):** Foi implementada a função `put-to-sleep-seconds` para pausar a thread. Ao passar o parâmetro 1, a aplicação aguarda pelo menos 2000 milissegundos, somados a um atraso adicional randômico de até 3000 milissegundos, imitando de maneira mais natural a navegação de um usuário humano.
* **Absorção Resiliente de Erros:** O processo de atualização com a função `update-product-price!` conta com tratamento explícito de exceções via `try/catch`. Se houver erro de *unique constraint* (produto já inserido no dia), o erro é ignorado e o log recebe a tag `:skipped`. Se o scraper falhar com outras exceções ou não encontrar o site (`nil`), os retornos são mapeados como `:failed` ou `:error` (sem levantar Exceptions para a camada do loop), de forma que o robô continue trabalhando pacificamente na próxima URL da fila.

## 5. Registro de Decisões (Decision Log)
*Espaço para pequenas decisões tomadas ao longo do refinamento.*

| Data | Decisão | Contexto / Justificativa | Status |
| :--- | :--- | :--- | :--- |
| 12/05 | Uso da biblioteca Chime para agendamento | Simplicidade e boa integração com threads nativas no ecossistema Clojure | Implementado |
| 13/05 | Uso do Jitter na função `put-to-sleep-seconds` | Evitar assinaturas de tempo idênticas entre as requisições que facilitam detecção de bots | Implementado |

## 6. Considerações Operacionais e de Escalabilidade
* **Plano de Rollout e Rollback:** *[Não fornecido no texto original - preencher se necessário]*
* **Observabilidade:** Acompanhamento via logs estruturados mapeando as tags `:skipped`, `:failed` e `:error` para monitoramento da saúde das coletas noturnas.
* **Segurança:** Uso de intervalos simulados humanos para evitar que o IP da infraestrutura seja banido pelos e-commerces monitorados.
* **Escalabilidade:** O design sequencial com `Thread/sleep` atual tem um limite máximo de vazão de dados diário. Quando o volume de inserções do sistema aumentar (ex: monitorar dezenas de milhares de itens), esse processamento batch terá que ser particionado em múltiplas filas assíncronas rodando atrás de redes de proxies para que se mantenha veloz e seguro contra banimentos ao mesmo tempo.
* **Testes Integrados do Job:** A suite de testes já prevê e garante que o sistema de Jobs ignore lojas não suportadas, contorne problemas do scraper como seções ausentes, lide bem com retornos de exceções e não quebre na presença de duplicidades no banco, garantindo um contrato sólido ao inserir ou ignorar linhas novas.