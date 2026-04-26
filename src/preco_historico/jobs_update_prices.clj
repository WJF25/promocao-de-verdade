(ns preco-historico.jobs-update-prices
  (:require
   [clojure.string :as st]
   [preco-historico.price-log :as price-log]
   [preco-historico.scraper :as scraper]))

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

(defn run-update-prices-job
  "Varre o banco e atualiza todos os preços monitorados."
  [datasource]
  (let [products (price-log/get-monitored-products datasource)]
    (println (str "Iniciando Job de atualização para " (count products) " produtos..."))
    (let [results (mapv (fn [product]
                          (let [res (update-product-price! datasource product)]
                            (Thread/sleep 2000)
                            res))
                        products)]
      (tap> {:job "update-prices" :results results})
      results)))