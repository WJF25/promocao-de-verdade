(ns preco-historico.core
  (:gen-class)
  (:require [preco-historico.config :as config]
            [preco-historico.db :as db]
            [preco-historico.server :as server]))

(defn -main
  [& _args]
  (let [cfg (config/load-config {:profile :dev})
        ds (db/make-datasource cfg)
        port (:http-port cfg)]
    (server/start-server! ds cfg port)
    (println "Servidor rodando na porta" port "...")))
