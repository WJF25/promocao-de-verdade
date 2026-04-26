(ns preco-historico.jobs-update-prices
  (:require
   [clojure.string :as st]
   [chime.core :as chime]
   [java-time.api :as jt]
   [preco-historico.config :as config]
   [preco-historico.db :as db]
   [preco-historico.price-log :as price-log]
   [preco-historico.scraper :as scraper])
  (:import [java.time LocalTime ZonedDateTime ZoneId]))

(defn update-product-price!
  "Lógica individual para atualizar um único produto."
  [datasource {:keys [url user_id]}]
  (try
    (if-let [selectors (scraper/get-selectors url)]
      (if-let [scraped-data (scraper/fetch-product-data url selectors)]
        (let [record (assoc scraped-data :user_id user_id)]
          (price-log/save-price-log! datasource record)
          {:url url :status :success})
        {:url url :status :failed :reason "Falha no scraper"})
      {:url url :status :failed :reason "Loja não suportada"})
    (catch Exception e
      ;; Se der 409 (duplicidade), ignoramos silenciosamente pois o robô já rodou hoje
      (if (st/includes? (ex-message e) "unique constraint")
        {:url url :status :skipped :reason "Já atualizado hoje"}
        {:url url :status :error :msg (ex-message e)}))))

(defn put-to-sleep-seconds [seconds]
  (Thread/sleep (* 1000 seconds)))

(defn run-update-prices-job
  "Varre o banco e atualiza todos os preços monitorados."
  [datasource]
  (let [products (price-log/get-monitored-products datasource)]
    (println (str "Iniciando Job de atualização para " (count products) " produtos..."))
    (let [results (mapv (fn [product]
                          (let [res (update-product-price! datasource product)]
                            (put-to-sleep-seconds 1)
                            res))
                        products)]
      (tap> {:job "update-prices" :results results})
      results)))

(defn start-scheduler! [datasource]
  (println "Iniciando agendador de preços (Chime)...")
  (let [sp-zone (ZoneId/of "America/Sao_Paulo")
        now (ZonedDateTime/now sp-zone)
        hoje-as-tres (jt/zoned-date-time (jt/local-date now)
                                         (jt/local-time 3 0)
                                         sp-zone)
        ;; Se já passou das 3h, começa amanhã. Se não, começa hoje.
        primeira-exec (if (jt/after? now hoje-as-tres)
                        (jt/plus hoje-as-tres (jt/days 1))
                        hoje-as-tres)
        schedule (chime/periodic-seq (jt/instant primeira-exec)
                                     (jt/period 1 :days))]
    (chime/chime-at schedule
                    (fn [time]
                      (println "Executando Job agendado em:" time)
                      (run-update-prices-job datasource))
                    {:error-handler (fn [e]
                                      (println "Erro no agendador:" (ex-message e))
                                      true)})))

(defn -main [& args]
  (println "### INICIANDO JOB DE PREÇOS ###")
  (let [cfg (config/load-config)
        ds (db/make-datasource cfg)]
    (try
      (let [results (run-update-prices-job ds)]
        (println "\n### RESUMO DO JOB ###")
        (println "Total processado:" (count results))
        (println "Sucessos:" (count (filter #(= :success (:status %)) results)))
        (println "Falhas/Pulos:" (count (filter #(not= :success (:status %)) results))))
      (finally
        ;; Importante fechar conexões ou garantir a saída do processo
        (println "Encerrando...")
        (shutdown-agents)))))