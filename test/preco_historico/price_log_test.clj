(ns preco-historico.price-log-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [preco-historico.config :as config]
            [preco-historico.db :as db]
            [preco-historico.price-log :as price-log]
            [preco-historico.user :as user]))

(defn- test-datasource []
  (-> (config/load-config {:profile :test})
      db/make-datasource))

(defn- ensure-schema! [ds]
  (db/create-extension-uuid! ds)
  (db/create-users-table! ds)
  (db/create-price-logs-table! ds))

(def ^:dynamic *test-ds* nil)

(defn price-log-fixture [f]
  (let [ds (test-datasource)]
    (ensure-schema! ds)
    (db/truncate-price-logs! ds)
    (db/truncate-users! ds)
    (binding [*test-ds* ds]
      (try
        (f)
        (finally
          (db/truncate-price-logs! ds)
          (db/truncate-users! ds))))))

(use-fixtures :each price-log-fixture)

(deftest save-price-log!-success-test
  (testing "persiste um price_log válido e retorna id e campos"
    (let [ds *test-ds*
          u (user/create-user! ds "price-log-ok@example.com" "secret123")
          row (price-log/save-price-log!
               ds
               {:user_id (:id u)
                :product_name "Nintendo Switch"
                :site_name "amazon"
                :price_original 2499.90M
                :price_cash 1999.90M
                :price_installment 2199.90M
                :max_installments 10
                :url "https://example.com/produto"})]
      (is (= "Nintendo Switch" (:product_name row)))
      (is (uuid? (:id row))))))

(deftest save-price-log!-unique-constraint-test
  (testing "rejeita duplicidade no mesmo dia (unique constraint do Postgres)"
    (let [ds *test-ds*
          u (user/create-user! ds "price-log-dup@example.com" "secret123")
          log {:user_id (:id u)
               :product_name "PlayStation 5"
               :site_name "kabum"
               :price_original 4599.00M
               :price_cash 4299.00M
               :price_installment 4499.00M
               :max_installments 12
               :url "https://example.com/ps5"}]
      (price-log/save-price-log! ds log)
      (is (thrown? Exception
                   (price-log/save-price-log! ds log))))))

