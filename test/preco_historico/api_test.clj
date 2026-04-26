(ns preco-historico.api-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [preco-historico.db :as db]
            [preco-historico.user :as user]
            [preco-historico.routes :as routes]
            [preco-historico.config :as config]
            [preco-historico.scraper :as scraper]
            [preco-historico.price-log :as price-log]
            [ring.mock.request :as mock]
            [cheshire.core :as json]))

;; Variável dinâmica para o DataSource de teste (o que vimos antes)
(def ^:dynamic *test-ds* nil)
(def ^:dynamic *test-cfg* nil)

(defn api-fixture [f]
  (let [cfg (config/load-config {:profile :test})
        ds (db/make-datasource cfg)]
    (binding [*test-ds* ds
              *test-cfg* cfg]
      ;; Limpa as tabelas antes de cada teste
      (db/truncate-price-logs! ds)
      (db/truncate-users! ds)
      (f))))

(use-fixtures :each api-fixture)

(defn- get-login-token [app email password]
  (let [req (-> (mock/request :post "/api/login")
                (mock/content-type "application/json")
                (mock/json-body {:email email :password password}))
        resp (app req)]
    (get (json/parse-string (:body resp) true) :token)))


(def mock-scraped-data
  {:product_name "Produto Mock"
   :site_name "Loja Mock"
   :price_original 1500.0
   :price_cash 1000.0
   :price_installment 1000.0
   :max_installments 10})

(deftest sanity-check
  (let [app (routes/app nil {})
        response (app (mock/request :get "/health"))]
    (is (= 200 (:status response)))))

(deftest consult-api-test
  (let [app (routes/app *test-ds* *test-cfg*)]
    (testing "Sucesso: Retorna dados raspados simulados no /consult"
      (with-redefs [scraper/get-selectors (fn [_] {:mock "selectors"})
                    scraper/fetch-product-data (fn [url _] (assoc mock-scraped-data :url url))]
        (let [payload {:url "https://mock.com/produto"}
              response (app (-> (mock/request :post "/api/prices/consult")
                                (mock/content-type "application/json")
                                (mock/json-body payload)))
              body (json/parse-string (:body response) true)]
          (is (= 200 (:status response)))
          (is (= "Produto Mock" (:product_name body)))
          (is (= 1000.0 (:price_cash body))))))))

(deftest save-price-log-api-test
  (let [app (routes/app *test-ds* *test-cfg*)
        email "teste@api.com"
        password "senha123"
        ;; 1. Primeiro precisamos de um usuário real no banco de teste
        _ (user/create-user! *test-ds* email password)
        token (get-login-token app email password)
        auth-header (str "Bearer " token)] ;; String para simular o JSON vindo da web

    (testing "Falha: Tentativa de salvar sem token (Não autorizado)"
      (let [payload {:product_name "Teclado" :site_name "Loja" :price_cash 100.0 :url "url"}
            response (app (-> (mock/request :post "/api/prices")
                              (mock/content-type "application/json")
                              (mock/json-body payload)))]
        (is (= 401 (:status response)))
        (is (= "Não autorizado. Token inválido ou ausente."
               (get (json/parse-string (:body response) true) :error)))))

    (testing "Falha: Enviando um mapa vazio"
      (let [payload nil
            response (app (-> (mock/request :post "/api/prices")
                              (mock/header "authorization" auth-header)
                              (mock/content-type "application/json")
                              (mock/json-body payload)))]
        (is (= 400 (:status response)))
        (is (= "URL é obrigatória"
               (-> response
                   (update-in [:body] json/parse-string true)
                   (get-in  [:body :error]))))))

    (testing "Falha: Enviando uma url de loja não atendida"
      (let [payload {:url "https://mock.com/produto"}
            response (app (-> (mock/request :post "/api/prices")
                              (mock/header "authorization" auth-header)
                              (mock/content-type "application/json")
                              (mock/json-body payload)))]
        (is (= 400 (:status response)))
        (is (= "Loja não suportada ainda."
               (-> response
                   (update-in [:body] json/parse-string true)
                   (get-in  [:body :error]))))))

    (testing "Sucesso: Salvando um log de preço usando Scraper interno"
      ;; Sequestramos a internet!
      (with-redefs [scraper/get-selectors (fn [_] {:mock "selectors"})
                    scraper/fetch-product-data (fn [url _] (assoc mock-scraped-data :url url))]

        (let [payload {:url "https://mock.com/produto-teste"}
              response (app (-> (mock/request :post "/api/prices")
                                (mock/header "authorization" auth-header)
                                (mock/content-type "application/json")
                                (mock/json-body payload)))
              body (json/parse-string (:body response) true)]

          (is (= 201 (:status response)))
          (is (= "Produto Mock" (:product_name body)))
          (is (string? (:user_id body))))))

    (testing "Erro: Duplicidade no mesmo dia (409)"
      (with-redefs [scraper/get-selectors (fn [_] {:mock "selectors"})
                    scraper/fetch-product-data (fn [url _] (assoc mock-scraped-data :url url))]
        (let [payload {:url "https://mock.com/produto-repetido"}
              ;; Primeira requisição passa
              _ (app (-> (mock/request :post "/api/prices")
                         (mock/header "authorization" auth-header)
                         (mock/content-type "application/json")
                         (mock/json-body payload)))
              ;; Segunda requisição no mesmo dia deve falhar
              response (app (-> (mock/request :post "/api/prices")
                                (mock/header "authorization" auth-header)
                                (mock/content-type "application/json")
                                (mock/json-body payload)))]

          (is (= 409 (:status response)))
          (is (= "Este produto já teve seu preço registrado hoje para este usuário."
                 (get-in (json/parse-string (:body response) true) [:error]))))))

    (testing "Falha: Scraper retorna nil"
      (with-redefs [scraper/get-selectors (fn [_] {:mock "selectors"})
                    scraper/fetch-product-data (fn [_ _] nil)]
        (let [response (app (-> (mock/request :post "/api/prices")
                                (mock/header "authorization" auth-header)
                                (mock/json-body {:url "https://loja.com/erro"})))]
          (is (= 422 (:status response)))
          (is (= "Não foi possível extrair os dados desta URL."
                 (get-in (json/parse-string (:body response) true) [:error]))))))

    (testing "Falha: Dados inválidos após extração Malli"
      (with-redefs [scraper/get-selectors (fn [_] {:mock "selectors"})
                    ;; Simulamos o scraper devolvendo algo que quebra o Schema (ex: nome faltando)
                    scraper/fetch-product-data (fn [_ _] {:product_name nil :price_cash 10.0})]
        (let [response (app (-> (mock/request :post "/api/prices")
                                (mock/header "authorization" auth-header)
                                (mock/json-body {:url "https://loja.com/invalido"})))]
          (is (= 422 (:status response)))
          (is (contains? (json/parse-string (:body response) true) :detalhes)))))

    (testing "Erro: Falha genérica "
      (with-redefs [scraper/get-selectors (fn [_] {:mock "selectors"})
                    scraper/fetch-product-data (fn [url _] (assoc mock-scraped-data :url url))
                    price-log/save-price-log! (fn [_ _] (throw (Exception. "Explodiu o banco")))]
        (let [response (app (-> (mock/request :post "/api/prices")
                                (mock/header "authorization" auth-header)
                                (mock/json-body {:url "https://loja.com/pane"})))]
          (is (= 500 (:status response)))
          (is (= "Erro interno ao salvar preço"
                 (get-in (json/parse-string (:body response) true) [:error]))))))))

(deftest vitrine-api-test
  (let [app (routes/app *test-ds* *test-cfg*)
        _ (user/create-user! *test-ds* "user1@teste.com" "123")
        _ (user/create-user! *test-ds* "user2@teste.com" "123")
        token1 (get-login-token app "user1@teste.com" "123")
        token2 (get-login-token app "user2@teste.com" "123")]

    (testing "Sucesso: Vitrine lista apenas os preços do usuário"
      ;; Simulamos e salvamos um produto pro User 1
      (with-redefs [scraper/get-selectors (fn [_] {:mock "selectors"})
                    scraper/fetch-product-data (fn [url _] (assoc mock-scraped-data :url url))]
        (app (-> (mock/request :post "/api/prices")
                 (mock/header "authorization" (str "Bearer " token1))
                 (mock/content-type "application/json")
                 (mock/json-body {:url "https://mock.com/meu-produto"}))))

      (testing "Consulta a vitrine do User 1"
        (let [response (app (-> (mock/request :get "/api/prices/vitrine")
                                (mock/header "authorization" (str "Bearer " token1))))
              body (json/parse-string (:body response) true)]

          (is (= 200 (:status response)))
          (is (= 1 (count body)))
          (is (= "Produto Mock" (:product_name (first body))))))

      (testing "Consulta a vitrine do User 2 - VAZIA"
        (let [response (app (-> (mock/request :get "/api/prices/vitrine")
                                (mock/header "authorization" (str "Bearer " token2))))
              body (json/parse-string (:body response) true)]

          (is (= 200 (:status response)))
          (is (= 0 (count body))))))))

(deftest login-api-test
  (let [app (routes/app *test-ds* *test-cfg*)
        email "login@teste.com"
        password "senha-secreta-123"]
    (user/create-user! *test-ds* email password)

    (testing "Sucesso: Login retorna 200 e um token JWT"
      (let [payload {:email email :password password}
            request (-> (mock/request :post "/api/login")
                        (mock/content-type "application/json")
                        (mock/json-body payload))
            response (app request)
            body (json/parse-string (:body response) true)]
        (is (= 200 (:status response)))
        (is (contains? body :token))
        (is (string? (:token body)))
        (is (= email (get-in body [:user :email])))))

    (testing "Erro: Senha incorreta retorna 401"
      (let [payload {:email email :password "senha-errada"}
            request (-> (mock/request :post "/api/login")
                        (mock/content-type "application/json")
                        (mock/json-body payload))
            response (app request)]

        (is (= 401 (:status response)))
        (is (= "E-mail ou senha inválidos" (get-in (json/parse-string (:body response) true) [:error])))))

    (testing "Erro: Usuário inexistente retorna 401"
      (let [payload {:email "fantasma@teste.com" :password password}
            request (-> (mock/request :post "/api/login")
                        (mock/content-type "application/json")
                        (mock/json-body payload))
            response (app request)]

        (is (= 401 (:status response)))))))


(deftest list-all-prices-test
  (let [app (routes/app *test-ds* *test-cfg*)
        email "user@lista.com"
        _ (user/create-user! *test-ds* email "123")
        token (get-login-token app email "123")
        auth-header (str "Bearer " token)]

    (testing "Sucesso: Listar histórico completo"
      (let [response (app (-> (mock/request :get "/api/prices")
                              (mock/header "authorization" auth-header)))]
        (is (= 200 (:status response)))))

    (testing "Falha: Listar sem token "
      (let [response (app (mock/request :get "/api/prices"))]
        (is (= 401 (:status response)))))))

(deftest consult-price-errors-test
  (let [app (routes/app *test-ds* *test-cfg*)]
    (testing "Falha: Loja não suportada no consult"
      (let [response (app (-> (mock/request :post "/api/prices/consult")
                              (mock/json-body {:url "https://uol.com.br"})))]
        (is (= 400 (:status response)))))

    (testing "Falha: Erro de extração no consult "
      (with-redefs [scraper/get-selectors (fn [_] {:mock "s"})
                    scraper/fetch-product-data (fn [_ _] nil)]
        (let [response (app (-> (mock/request :post "/api/prices/consult")
                                (mock/json-body {:url "https://amazon.com.br/bug"})))]
          (is (= 422 (:status response))))))))

(deftest vitrine-errors-test
  (let [app (routes/app *test-ds* *test-cfg*)]
    (testing "Falha: Erro inesperado na vitrine (Linha 104)"
      (let [email "bug@vitrine.com"
            _ (user/create-user! *test-ds* email "123")
            token (get-login-token app email "123")]
        (with-redefs [price-log/get-vitrine-prices (fn [_ _] (throw (Exception. "DB Down")))]
          (let [response (app (-> (mock/request :get "/api/prices/vitrine")
                                  (mock/header "authorization" (str "Bearer " token))))]
            (is (= 500 (:status response)))))))))

(deftest delete-price-api-test
  (let [app (routes/app *test-ds* *test-cfg*)
        email "delete@teste.com"
        _ (user/create-user! *test-ds* email "123")
        token (get-login-token app email "123")
        auth-header (str "Bearer " token)]

    (testing "Sucesso: Deletar um registro existente"
      ;; Primeiro salvamos algo para ter o ID
      (with-redefs [scraper/get-selectors (fn [_] {:mock "s"})
                    scraper/fetch-product-data (fn [url _] (assoc mock-scraped-data :url url))]
        (let [save-resp (app (-> (mock/request :post "/api/prices")
                                 (mock/header "authorization" auth-header)
                                 (mock/json-body {:url "https://loja.com/item-para-deletar"})))
              log-id (:id (json/parse-string (:body save-resp) true))

              ;; Agora deletamos
              delete-resp (app (-> (mock/request :delete (str "/api/prices/storage/" log-id))
                                   (mock/header "authorization" auth-header)))]
          (is (= 200 (:status delete-resp)))
          (is (= "Registro removido com sucesso" (get (json/parse-string (:body delete-resp) true) :message))))))

    (testing "Falha: Tentar deletar ID inexistente (404)"
      (let [random-id (java.util.UUID/randomUUID)
            response (app (-> (mock/request :delete (str "/api/prices/storage/" random-id))
                              (mock/header "authorization" auth-header)))]
        (is (= 404 (:status response)))))))