(ns preco-historico.server
  (:require
   [preco-historico.jobs-update-prices :as jobs]
   [preco-historico.routes :as routes]
   [ring.adapter.jetty :as jetty]))

(defn start-server!
  [datasource config port]
  (jobs/start-scheduler! datasource)
  (jetty/run-jetty (routes/app datasource config) {:port port :join? false}))
