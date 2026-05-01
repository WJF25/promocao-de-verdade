# Preço Histórico 📈

Um ecossistema robusto em **Clojure** para monitoramento e análise de preços de e-commerce brasileiro. O projeto combina web scraping inteligente, persistência em PostgreSQL e automação de tarefas.

## ✨ Destaques
- **Scraper Resiliente**: Extração de dados via Jsoup para Amazon, Mercado Livre e Magazine Luiza.
- **Cobertura de Testes**: >95% de forms cobertos, garantindo estabilidade em cada handler e serviço.
* **Automação (Jobs)**: Robô integrado para atualização diária de preços sem intervenção humana.
* **Segurança**: Autenticação via JWT e validação de esquemas rigorosa com Malli.

## Nota de Projeto (Build to Learn): 
Este projeto foi desenvolvido com o objetivo explícito de aprofundamento técnico em Clojure e arquitetura de sistemas resilientes. Mais do que uma ferramenta de monitoramento, ele é o resultado de um estudo prático sobre robustez e qualidade de software.

### Destaques de Implementação & Novos Aprendizados
Este projeto serviu para explorar bibliotecas específicas do ecossistema Clojure e estratégias de automação:
- *Web Scraping com Jsoup:* Implementação de extração de dados resiliente utilizando seletores CSS para navegar no DOM de grandes e-commerces.
- *Acesso a Dados com HugSQL:* Utilização do HugSQL para manter o SQL "limpo" em arquivos externos, permitindo uma separação clara entre a lógica de persistência e o código Clojure.
- *Arquitetura Multi-Entry `(CLI & Job)`:* Implementação de um ponto de entrada secundário `(-main)` focado em tarefas de manutenção (Jobs), permitindo a execução de rotinas de atualização diretamente via terminal de forma independente do servidor API.
- *Resiliência em Processamento Batch:* Criação de uma rotina de atualização com controle de intervalos `(Thread/sleep)` e tratamento de exceções para lidar com as variações de resposta de servidores externos durante execuções em massa.

## 🛠 Tech Stack
- **Linguagem**: Clojure (Leiningen) 
- **Banco de Dados**: PostgreSQL + HugSQL
- **Parsing HTML**: Jsoup
* **Roteamento**: Reitit + Ring

## 🚀 Como Iniciar

### 1. Infraestrutura
Certifique-se de ter o Docker instalado e suba o banco de dados:
```bash
docker-compose up -d
```

### 2. Migrações
As tabelas de users e price_logs serão criadas automaticamente conforme as definições em resources/migrations/

### 3. Execução
Para rodar o servidor API:
```bash
lein run
```

Para rodar o Job de atualização de preços via terminal:

```bash
lein run -m preco-historico.jobs-update-prices
```

### 4.Testes e Cobertura
Para rodar a suite de testes e gerar o relatório de cobertura:
```
lein cloverage
```

## License

Copyright © 2026 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
