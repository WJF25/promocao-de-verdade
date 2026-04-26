(ns preco-historico.server
  (:require [preco-historico.routes :as routes]
            [ring.adapter.jetty :as jetty]))

(defn start-server!
  [datasource config port]
  (jetty/run-jetty (routes/app datasource config) {:port port :join? false}))
