(ns preco-historico.price-log
  (:require [preco-historico.db :as db]))

(defn save-price-log!
  [datasource log-data]
  (db/insert-price-log! datasource log-data))

(defn find-by-user [datasource user-id]
  (db/find-prices-by-user datasource {:user_id user-id}))

(defn get-vitrine-prices
  "Busca os preços mais recentes e sem repetição para a vitrine do usuário."
  [datasource user-id]
  (db/get-latest-prices-by-user datasource {:user_id user-id}))

(defn delete-price-log! [datasource params]
  (db/delete-price-log! datasource params))

(defn get-monitored-products [datasource]
  (db/get-all-monitored-products datasource))