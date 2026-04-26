-- :name create-extension-uuid! :!
-- :doc Cria a extensão para UUID se não existir
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- :name create-users-table! :!
-- :doc Cria a tabela de usuários
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP WITH TIME ZONE
);

-- :name create-price-logs-table! :!
-- :doc Cria a tabela de histórico de preços
CREATE TABLE IF NOT EXISTS price_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_name TEXT NOT NULL,
    site_name VARCHAR(50) NOT NULL,
    price_original DECIMAL(12, 2),
    price_cash DECIMAL(12, 2) NOT NULL CHECK (price_cash >= 0),
    price_installment DECIMAL(12, 2),
    max_installments INTEGER CHECK (max_installments IS NULL OR max_installments > 0),
    url TEXT NOT NULL,
    captured_at DATE NOT NULL DEFAULT CURRENT_DATE,
    UNIQUE(user_id, product_name, site_name, captured_at)
);

-- :name truncate-users! :!
-- :doc Limpa a tabela de usuários para testes
TRUNCATE TABLE users CASCADE;

-- :name truncate-price-logs! :!
-- :doc Limpa a tabela de histórico de preços para testes
TRUNCATE TABLE price_logs CASCADE;

-- :name insert-user! :<! :1
-- :doc Insere um usuário e retorna o ID e o Email
INSERT INTO users (email, password_hash)
VALUES (:email, :password_hash)
RETURNING id, email, created_at;

-- :name insert-price-log! :<! :1
-- :doc Insere um registro de histórico de preços e retorna a linha completa
INSERT INTO price_logs (
  user_id,
  product_name,
  site_name,
  price_original,
  price_cash,
  price_installment,
  max_installments,
  url
  --~ (when (:captured_at params) ", captured_at")
)
VALUES (
  :user_id,
  :product_name,
  :site_name,
  :price_original,
  :price_cash,
  :price_installment,
  :max_installments,
  :url
  --~ (when (:captured_at params) ", :captured_at")
)
RETURNING *;

-- :name find-user-by-email :? :1
-- :doc Busca um usuário pelo email para autenticação
SELECT id, email, password_hash FROM users WHERE email = :email

-- :name find-prices-by-user :? :*
-- :doc Busca todos os registros de preços de um usuário específico
SELECT *
FROM price_logs
WHERE user_id = :user_id
ORDER BY captured_at DESC, product_name ASC;

-- :name get-latest-prices-by-user :? :*
-- :doc Recupera o preço mais recente (vitrine) de cada produto (URL) para um usuário específico
SELECT DISTINCT ON (url) 
       id, url, product_name, price_original, price_cash, price_installment, site_name, captured_at
FROM price_logs
WHERE user_id = :user_id
ORDER BY url, captured_at DESC;

-- :name delete-price-log! :! :n
-- :doc Apaga um registro de preço específico de um usuário
DELETE FROM price_logs
WHERE id = :id AND user_id = :user_id;

-- :name get-all-unique-urls :? :*
-- :doc Busca todas as URLs únicas salvas no sistema para atualização automática
SELECT DISTINCT url, site_name FROM price_logs;

-- :name get-all-monitored-products :? :*
-- :doc Busca todas as combinações únicas de URL e Usuário para atualização
SELECT DISTINCT ON (url, user_id) url, user_id 
FROM price_logs
ORDER BY url, user_id;