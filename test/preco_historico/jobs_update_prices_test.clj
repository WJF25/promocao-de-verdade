(ns preco-historico.jobs-update-prices-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [preco-historico.db :as db]
            [preco-historico.user :as user]
            [preco-historico.price-log :as price-log]
            [preco-historico.scraper :as scraper]
            [preco-historico.jobs-update-prices :as jobs]
            [preco-historico.api-test :refer [api-fixture *test-ds*]]
            [cheshire.core :as json]
            [chime.core :as chime]
            [java-time.api :as jt]))

(use-fixtures :each api-fixture)

(deftest update-prices-job-test
  (let [email "robot@test.com"
        _ (user/create-user! *test-ds* email "123")
        user (db/find-user-by-email *test-ds* {:email email})
        user-id (:id user)
        url "https://amazon.com.br/produto-teste"
        url2 "https://kabum.com.br/produto-teste2"
        url3 "https://mercadolivre.com.br/produto-teste3"]

    (testing "Sucesso: O Job deve atualizar preços com sucesso"
      ;; 1. Inserimos um log manual inicial
      (price-log/save-price-log! *test-ds* {:product_name "Teste"
                                            :site_name "Amazon"
                                            :price_original 110.0
                                            :price_cash 100.0
                                            :price_installment 130.0
                                            :max_installments 10
                                            :url url
                                            :user_id user-id})

      ;; 2. Rodamos o Job mockando o scraper para retornar um preço novo
      (with-redefs [scraper/get-selectors (fn [_] {:name "h1"})
                    scraper/fetch-product-data (fn [u _]
                                                 {:product_name "Teste02" :site_name "Amazon" :price_cash 100.0 :url u :price_original 110.0 :price_installment 130.0 :max_installments 10})
                    println (fn [& _] nil)
                    jobs/put-to-sleep-seconds (fn [_] nil)]
        (let [results (jobs/run-update-prices-job *test-ds*)]
          (is (= 1 (count results)))
          (is (= :success (:status (first results))))

          ;; 3. Verificamos se agora temos 2 registros no banco para essa URL
          (let [history (price-log/find-by-user *test-ds* user-id)]
            (is (= 2 (count history)))))))

    (testing "Falha: Job não consegue atualizar preços de uma URL não suportada"
      ;; 1. Inserimos um log manual inicial
      (price-log/save-price-log! *test-ds* {:product_name "Teste21"
                                            :site_name "Kabom"
                                            :price_original 710.0
                                            :price_cash 500.0
                                            :price_installment 930.0
                                            :max_installments 10
                                            :url url2
                                            :user_id user-id})

      (with-redefs [scraper/get-selectors (fn [_] nil)
                    jobs/put-to-sleep-seconds (fn [_] nil)
                    println (fn [& _] nil)]
        (let [results (jobs/run-update-prices-job *test-ds*)]
          (is (= 2 (count results)))
          (is (= :failed (:status (last results))))

          ;; 3. Verificamos se agora temos 2 registros no banco para essa URL
          (let [history (price-log/find-by-user *test-ds* user-id)]
            (is (= 3 (count history)))))))




    (testing "Falha: Preço já atualizado no dia de hoje"
      #_(price-log/save-price-log! *test-ds* {:product_name "Teste21"
                                              :site_name "Kabom"
                                              :price_original 710.0
                                              :price_cash 500.0
                                              :price_installment 930.0
                                              :max_installments 10
                                              :url url2
                                              :user_id user-id})

      (with-redefs [scraper/get-selectors (fn [_] {:name "h1"})
                    scraper/fetch-product-data (fn [_ _]
                                                 {:product_name "Teste21"
                                                  :site_name "Kabom"
                                                  :price_original 710.0
                                                  :price_cash 500.0
                                                  :price_installment 930.0
                                                  :max_installments 10
                                                  :url url2
                                                  :user_id user-id})
                    jobs/put-to-sleep-seconds (fn [_] nil)
                    println (fn [& _] nil)]
        (let [results (jobs/run-update-prices-job *test-ds*)]
          (is (= 2 (count results)))
          (is (= :skipped (:status (last results))))

          ;; 3. Verificamos se agora temos 2 registros no banco para essa URL
          (let [history (price-log/find-by-user *test-ds* user-id)]
            (is (= 3 (count history)))))))

    (testing "Falha: Falha no scraper"
      (with-redefs [scraper/get-selectors (fn [_] {:name "h1"})
                    scraper/fetch-product-data (fn [_ _] nil)
                    jobs/put-to-sleep-seconds (fn [_] nil)
                    println (fn [& _] nil)]
        (let [results (jobs/run-update-prices-job *test-ds*)]
          (is (= 2 (count results)))
          (is (= :failed (:status (last results))))
          (is (= "Falha no scraper" (:reason (last results))))

          (let [history (price-log/find-by-user *test-ds* user-id)]
            (is (= 3 (count history)))))))

    (testing "Falha: Erro no scraper de um anuncio removido - url não existe mais"
      (price-log/save-price-log! *test-ds* {:product_name "Teste22"
                                            :site_name "Mercado Livre"
                                            :price_original 1110.0
                                            :price_cash 900.0
                                            :price_installment 1110.0
                                            :max_installments 10
                                            :url url3
                                            :user_id user-id})
      (with-redefs [scraper/get-selectors (fn [_] {:name "h1"})
                    scraper/fetch-product-data (fn [_ _]
                                                 (throw (Exception. "Anuncio removido")))
                    jobs/put-to-sleep-seconds (fn [_] nil)
                    println (fn [& _] nil)]
        (let [results (jobs/run-update-prices-job *test-ds*)]
          (is (= 3 (count results)))
          (is (= :error (:status (last results))))
          (is (= "Anuncio removido" (:msg (last results))))

          (let [history (price-log/find-by-user *test-ds* user-id)]
            (is (= 4 (count history)))))))

    (testing "Sucesso: O Job deve pular lojas não suportadas sem travar"
      (with-redefs [scraper/get-selectors (fn [_] nil)
                    jobs/put-to-sleep-seconds (fn [_] nil)
                    println (fn [& _] nil)]
        (let [results (jobs/run-update-prices-job *test-ds*)]
          (is (= :failed (:status (first results))))
          (is (= "Loja não suportada" (:reason (first results)))))))))



(deftest scheduler-config-test
  (testing "Sucesso:Configuração e inicialização do Chime"
    (let [chime-started (atom false)]
      (with-redefs [chime/chime-at (fn [schedule _ options]
                                     (reset! chime-started true)
                                     ;; Verifica se o schedule é uma sequência de horários
                                     (is (seq? schedule))
                                     ;; Garante que o tratador de erro foi definido
                                     (is (fn? (:error-handler options)))
                                     (fn [] :closed))
                    println (fn [& _] nil)
                    jobs/run-update-prices-job (fn [_] (println "Mock: Job não executado"))]
        (jobs/start-scheduler! *test-ds*)
        (is @chime-started "O agendador Chime deveria ter sido disparado"))))

  (testing "Resiliência do Error Handler do agendador"
    (let [captured-handler (atom nil)]
      (with-redefs [chime/chime-at (fn [_ _ options]
                                     (reset! captured-handler (:error-handler options))
                                     (fn [] :closed))
                    jobs/run-update-prices-job (fn [_] (println "Mock: Job não executado"))
                    println (fn [& _] nil)]
        (jobs/start-scheduler! *test-ds*)
        (let [handler @captured-handler]
          (is (handler (Exception. "Simulação de falha no banco")))
          (is (= true (handler (Exception. "Outra falha")))))))))