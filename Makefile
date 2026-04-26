.PHONY: test logs bash restart

# Roda um teste de cada vez dentro do container
testp:
	docker exec crojuru lein test $(ARGS)

# Roda os testes dentro do container
test:
	docker exec crojuru lein test 

# Entra no terminal do container (caso você precise investigar algo)
bash:
	docker exec -it crojuru bash

# Vê os logs da aplicação ao vivo
logs:
	docker logs -f crojuru

# Reinicia a aplicação
restart:
	docker compose restart app

# Cria os bancos de dados de dev e teste
setup-db:
	docker exec -it preco-historico-db psql -U postgres -c "CREATE DATABASE preco_historico_dev;" || true
	docker exec -it preco-historico-db psql -U postgres -c "CREATE DATABASE preco_historico_test;" || true

# roda o teste de cobertura
coverage:
	docker exec -it crojuru lein cloverage