# Blindagem e Segurança por Design (Auth e Schemas)

**Autores:** Waldiney
**Revisores:** Bruno Ferraz
**Status:** Implementado
**Data:** Maio/2026

## 1. Contexto e Necessidade
*Qual é o problema que estamos tentando resolver?*
Um ecossistema que lida com o rastreamento histórico de preços e executa processamentos massivos em background requer fronteiras rígidas de entrada. Sem validações estritas, a API estaria suscetível a anomalias de dados e vulnerabilidades de injeção. 

Além disso, a gestão de identidade precisava ser implementada de forma que não sobrecarregasse o banco de dados (PostgreSQL) a cada requisição nas rotas protegidas da API, garantindo segurança sem sacrificar a performance do monitoramento.

## 2. Objetivos e Não-Objetivos
*O que define o sucesso e o que está fora de escopo?*
* **Objetivos:**
  * Garantir "Segurança por Design": senhas nunca trafegam ou são armazenadas em texto puro.
  * Fornecer autenticação escalável via JWT (*JSON Web Tokens*), separando a identidade do estado da aplicação.
  * Blindar a camada de controle (*Controllers/Handlers*) contra dados não mapeados utilizando verificação formal de contratos (Malli).
  * Assegurar a estabilidade da segurança com uma cobertura de testes unitários e de integração superior a 95%.
* **Não-Objetivos:**
  * Não é objetivo, neste momento, implementar RBAC (*Role-Based Access Control* - Níveis de permissão granulares) ou uma infraestrutura complexa de invalidação instantânea de tokens (ex: Redis Blocklist).

## 3. Soluções Propostas e Trade-offs
*Quais caminhos podemos seguir e por que escolhemos este?*

### 3.1. Solução A: JWT Stateless + Buddy Bcrypt + Malli Closed Schemas (Implementada)
Adoção da biblioteca `buddy` para gerenciamento de criptografia e tokens. As senhas são transformadas em hash utilizando o algoritmo bcrypt+sha512. A sessão do usuário não existe no banco; ela é carregada na assinatura do token JWT. A entrada de dados HTTP é filtrada pelo malli, rejeitando chaves extras na raiz com o modificador `[:closed true]`.

### 3.2. Solução B: Controle de Sessão em Banco de Dados (Stateful)
Utilizar cookies e manter uma tabela de `sessions` ativa no PostgreSQL, validando cada requisição HTTP contra essa tabela.

### 3.3. Comparativo e Justificativa (Por que A e não B?)
* **Vantagens da Solução A:** O banco de dados só é consultado no momento do Login para verificar a senha. Todas as rotas subsequentes operam de forma isolada e rápida validando apenas a assinatura matemática do JWT. Os esquemas do Malli impedem o envio de dados que não pertencem ao escopo da requisição.
* **Desvantagens da Solução A (Trade-off):** A natureza *stateless* do JWT traz o ônus da impossibilidade de revogação nativa. Um token emitido permanece válido por todo o seu tempo de vida de 24 horas, mesmo que a senha original do usuário seja alterada no meio do caminho. Aceitamos este risco temporal para manter a arquitetura simples e de fácil escalabilidade durante a fase de prototipação da ferramenta.
* **Por que a Solução B foi descartada:** Aumentaria exponencialmente o *overhead* de I/O (leitura) no banco de dados para cada simples consulta de preço. O JWT resolve o problema de verificação de identidade localmente através da chave secreta (`JWT_SECRET`) injetada via variáveis de ambiente.

## 4. Design Detalhado
Detalhes técnicos da solução escolhida:
* **Criptografia Fortificada:** A função `hash-password` aplica salt nativamente via `buddy.hashers`, frustrando ataques de *rainbow tables*.
* **Contratos e Validation Middleware:** Todo payload é decodificado via `malli.transform` antes de atingir a regra de negócio. Isso protege a camada do HugSQL contra tipos de dados incompatíveis, atuando como o primeiro "firewall lógico" da aplicação.
* **TDD Aplicado à Segurança:** As rotas e lógicas de autenticação nasceram acompanhadas de testes abrangentes (`auth_test.clj`, `user_test.clj`), garantindo a recusa de senhas inválidas, bloqueio de chaves indesejadas e expiração correta dos tokens JWT. O projeto utiliza o plugin `lein-cloverage` para monitorar a rigidez dessas defesas, visando sempre mais de 95% de coverage global.

## 5. Registro de Decisões (Decision Log)
*Espaço para pequenas decisões tomadas ao longo do refinamento.*

| Data | Decisão | Contexto / Justificativa | Status |
| :--- | :--- | :--- | :--- |
| --/05 | Uso de Bcrypt + SHA512 via Buddy | Alto custo computacional para hashing, dificultando ataques de força bruta | Implementado |
| --/05 | Validação estrita com lein-cloverage | Exigir cobertura >95% especificamente em módulos críticos de Auth e Users | Implementado |

## 6. Considerações Operacionais e de Escalabilidade
* **Plano de Rollout e Rollback:** *[Não fornecido no texto original - preencher se necessário]*
* **Observabilidade:** Configuração de logs de auditoria para tentativas falhas de login e requests com payloads inválidos interceptados pelo Malli.
* **Segurança:** Uso de variáveis de ambiente (`JWT_SECRET`) gerenciadas de forma segura fora do código-fonte para assinar os tokens.
* **Escalabilidade Horizontal:** Se no futuro o "Preço Histórico" precisar rodar atrás de um *Load Balancer* com múltiplas instâncias da API Clojure operando em paralelo, a autenticação JWT funcionará sem nenhuma modificação, pois a identidade flutua no header HTTP da requisição e não na memória de um servidor específico.