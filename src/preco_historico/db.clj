(ns preco-historico.db
  (:require [hugsql.core :as hugsql]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [next.jdbc :as jdbc]))


;; Diz ao HugSQL para usar o next.jdbc por padrão
(hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc))

(defn make-datasource
  "Monta um datasource do next.jdbc a partir do mapa de configuração."
  [{:keys [db-host db-port db-user db-password db-name]}]
  (jdbc/get-datasource
   {:dbtype "postgresql"
    :host db-host
    :port db-port
    :user db-user
    :password db-password
    :dbname db-name}))

(hugsql/def-db-fns "sql/queries.sql")

; ai pililiu