(ns preco-historico.user-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [preco-historico.config :as config]
            [preco-historico.db :as db]
            [preco-historico.handler.user :as handler.user]
            [preco-historico.user :as user]))

(defn- test-datasource []
  (-> (config/load-config {:profile :test})
      db/make-datasource))

(defn- ensure-schema! [ds]
  (db/create-extension-uuid! ds)
  (db/create-users-table! ds))

(def ^:dynamic *test-ds* nil)

(defn user-fixture [f]
  (let [ds (test-datasource)]
    (ensure-schema! ds)
    (db/truncate-users! ds)
    (binding [*test-ds* ds]
      (try
        (f)
        (finally
          (db/truncate-users! ds))))))

(use-fixtures :each user-fixture)

(deftest create-user!-integration-test
  (testing "persiste usuário com email correto e id gerado pelo Postgres"
    (let [email "integration-test@example.com"
          plain "a-secure-password"
          row (user/create-user! *test-ds* email plain)]
      (is (= email (:email row)))
      (is (uuid? (:id row)))
      (is (some? (:created_at row))))))

(deftest duplicate-email-test
  (testing "handler retorna 409 e mensagem amigável quando o e-mail já existe"
    (let [email "duplicado@teste.com"
          password "secret123"
          ds *test-ds*]
      (user/create-user! ds email password)
      (let [handler (handler.user/create-user-handler ds)
            response (handler {:body {:email email :password password}})]
        (is (= 409 (:status response)))
        (is (= "E-mail já cadastrado" (get-in response [:body :error])))))))

(deftest validation-extra-key-test
  (testing "handler 400 com :detalhes quando o body tem chave não permitida (schema fechado)"
    (let [handler (handler.user/create-user-handler *test-ds*)
          response (handler {:body {:email "x@y.com" :password "secret" :extra "no"}})]
      (is (= 400 (:status response)))
      (is (= "Dados inválidos" (get-in response [:body :error])))
      (is (map? (get-in response [:body :detalhes])))
      (is (some? (get-in response [:body :detalhes]))))))

(deftest validation-types-test
  (testing "handler 400 com :detalhes quando email não é string"
    (let [handler (handler.user/create-user-handler *test-ds*)
          response (handler {:body {:email 12345 :password "secret"}})]
      (is (= 400 (:status response)))
      (is (= "Dados inválidos" (get-in response [:body :error])))
      (is (contains? (get-in response [:body :detalhes]) :email)))))

(deftest validation-missing-keys-test
  (testing "handler 400 com :detalhes quando falta :password"
    (let [handler (handler.user/create-user-handler *test-ds*)
          response (handler {:body {:email "only@mail.com"}})]
      (is (= 400 (:status response)))
      (is (= "Dados inválidos" (get-in response [:body :error])))
      (is (contains? (get-in response [:body :detalhes]) :password)))))
