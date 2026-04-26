(ns preco-historico.db-test
  (:require [clojure.test :refer [deftest is testing]]
            [preco-historico.config :as config]))

(deftest load-config-test-profile
  (testing "load-config respeita profile :test para db-name"
    (let [cfg (config/load-config {:profile :test})]
      (is (= "preco_historico_test" (:db-name cfg))))))

(deftest load-config-test-profile
  (testing "load-config respeita profile :test para db-name"
    (let [cfg (config/load-config {:profile :dev})]
      (is (= "preco_historico_dev" (:db-name cfg))))))
