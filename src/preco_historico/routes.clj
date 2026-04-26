(ns preco-historico.routes
  (:require
   [buddy.auth.middleware :refer [wrap-authentication]]
   [buddy.auth.backends :as backends]
   [preco-historico.handler.auth :as handler.auth]
   [preco-historico.handler.user :as handler.user]
   [preco-historico.handler.price-log :as handler.price-log]
   [reitit.ring :as reitit-ring]
   [reitit.ring.middleware.exception :as exception]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]))

(defn health-check-handler [_]
  {:status 200
   :body {:status "ok"}})

(defn app
  [datasource config]
  (let [secret (:secret config)
        auth-backend (backends/jws {:secret secret
                                    :token-name "Bearer"})]
    (reitit-ring/ring-handler
     (reitit-ring/router
      [["/health" {:get health-check-handler}]
       ["/api"
        ["/login" {:post (handler.auth/login-handler datasource secret)}]
        ["/users" {:post (handler.user/create-user-handler datasource)}]
        ;; --- Rotas Protegidas (Middleware de Autenticação aqui) ---
        ["/prices" {:middleware [[wrap-authentication auth-backend]]}
         ["" {:post (handler.price-log/save-price-log-handler datasource)
              :get  (handler.price-log/get-prices-handler datasource)}]
         ["/consult" {:post (handler.price-log/consult-price-handler datasource)}]
         ["/vitrine" {:get (handler.price-log/get-vitrine-handler datasource)}]
         ["/storage/:id" {:delete (handler.price-log/delete-price-log-handler datasource)}]]]]
      ;; Configuração de dados do roteador:
      {:data {:middleware [wrap-json-response
                           [wrap-json-body {:keywords? true}]
                           exception/exception-middleware]}})
     ;; Handler padrão para rotas não encontradas (404)
     (reitit-ring/create-default-handler
      {:not-found (fn [_] {:status 404 :body {:error "Rota não encontrada"}})}))))