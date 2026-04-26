(ns preco-historico.handler.price-log
  (:require [preco-historico.price-log :as price-log]
            [preco-historico.scraper :as scraper]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [clojure.string :as string]))

;; O Schema: O que não estiver aqui, o Malli barra!
(def PriceLogSchema
  [:map {:closed true}
   [:product_name :string]
   [:user_id :uuid]
   [:site_name :string]
   [:price_cash :double]
   [:url :string]
   [:price_original {:optional true} :double]
   [:price_installment {:optional true} :double]
   [:max_installments {:optional true} :int]])


(defn ->transforma-body [body]
  (m/decode PriceLogSchema body (mt/transformer mt/json-transformer)))

(defn- validate-price-log [record]
  (let [body-atualizado (->transforma-body record)]
    (when-not (m/validate PriceLogSchema body-atualizado)
      (me/humanize (m/explain PriceLogSchema body-atualizado)))))

(defn save-price-log-handler [datasource]
  (fn [request]
    (let [url (get-in request [:body :url])
          identity (:identity request)
          user-id-str (:user_id identity)
          selectors (scraper/get-selectors url)]
      (tap> {:identity identity
             :url url
             :selectors selectors
             :user-id-str user-id-str})
      (cond
        (not url)
        {:status 400 :body {:error "URL é obrigatória"}}
        (not identity)
        {:status 401 :body {:error "Não autorizado. Token inválido ou ausente."}}
        (not selectors)
        {:status 400 :body {:error "Loja não suportada ainda."}}
        :else
        (let [scraped-data (scraper/fetch-product-data url selectors)]

          (if (nil? scraped-data)
            {:status 422 :body {:error "Não foi possível extrair os dados desta URL."}}
            (let [user-id (java.util.UUID/fromString user-id-str)
                  record-to-save (merge {:max_installments 0}
                                        (-> scraped-data
                                            (update :price_installment #(or % 0.0))
                                            (update :price_original #(or % (:price_cash scraped-data))))
                                        {:user_id user-id})
                  erros-validacao (validate-price-log record-to-save)]
              (tap> {:dados-to-save record-to-save
                     :tipo-dados (type (:max_installments record-to-save))})
              (if erros-validacao
                {:status 422 :body {:error "Dados do preço inválidos após a extração"
                                    :detalhes erros-validacao}}
                (try
                  (let [result (price-log/save-price-log! datasource record-to-save)]
                    (tap> {:acao "Salvar Banco" :dados record-to-save})
                    {:status 201 :body result})
                  (catch Exception e
                    (if (clojure.string/includes? (ex-message e) "price_logs_user_id_product_name_site_name_captured_at_key")
                      {:status 409 :body {:error "Este produto já teve seu preço registrado hoje para este usuário."}}
                      {:status 500 :body {:error "Erro interno ao salvar preço" :msg (ex-message e)}})))))))))))


(defn get-prices-handler [datasource]
  (fn [request]
    (if-let [identity (:identity request)]
      (let [user-id (java.util.UUID/fromString (:user_id identity))
            prices (price-log/find-by-user datasource user-id)]
        (tap> prices)
        {:status 200 :body prices})
      {:status 401 :body {:error "Não autorizado"}})))

(defn consult-price-handler [_datasource]
  (fn [request]
    (let [url (get-in request [:body :url])
          selectors (scraper/get-selectors url)]
      (if-not selectors
        {:status 400 :body {:error "Loja não suportada ainda."}}
        (let [result (scraper/fetch-product-data url selectors)]
          (if result
            {:status 200 :body result}
            {:status 422 :body {:error "Não foi possível extrair dados desta URL."}}))))))

(defn get-vitrine-handler [datasource]
  (fn [request]
    (if-let [identity (:identity request)]
      (try
        (let [user-id (java.util.UUID/fromString (:user_id identity))
              ;; Chamamos a sua camada de serviço limpa e encapsulada
              prices (price-log/get-vitrine-prices datasource user-id)]
          (tap> {:acao "Carregar Vitrine" :total_itens (count prices)})
          {:status 200 :body prices})
        (catch Exception e
          {:status 500 :body {:error "Erro ao carregar a vitrine" :msg (ex-message e)}}))

      {:status 401 :body {:error "Não autorizado"}})))

(defn delete-price-log-handler [datasource]
  (fn [request]
    (let [identity (:identity request)
          user-id-str (:user_id identity)
          ;; Pegamos o ID que virá na URL: /api/prices/:id
          log-id-str (get-in request [:path-params :id])]

      (cond
        (not identity)
        {:status 401 :body {:error "Não autorizado"}}

        (not (and (string? log-id-str) log-id-str (re-matches #"[0-9a-fA-F-]{36}" log-id-str)))
        {:status 400 :body {:error "ID de registro inválido"}}
        :else
        (try
          (let [user-id (java.util.UUID/fromString user-id-str)
                log-id (java.util.UUID/fromString log-id-str)
                affected-rows (price-log/delete-price-log! datasource {:id log-id :user_id user-id})]
            (tap> {:id log-id :user_id user-id :affected-rows affected-rows})
            (if (> affected-rows 0)
              {:status 200 :body {:message "Registro removido com sucesso"}}
              {:status 404 :body {:error "Registro não encontrado ou não pertence ao usuário"}}))
          (catch Exception e
            {:status 500 :body {:error "Erro ao deletar registro" :msg (ex-message e)}}))))))

;; TODO: o erro de fetch data só volta nil e usa uma resposta genérica, não seria melhor tratar esse erro vendo a causa correta?