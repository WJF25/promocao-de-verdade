(ns preco-historico.scraper-test
  (:require [clojure.test :refer [deftest is testing]]
            [preco-historico.scraper :as scraper]
            [clj-http.client :as client]))

;; Referência para testarmos as funções privadas diretamente
(def parse-price #'scraper/parse-price)
(def parse-installment #'scraper/parse-installment)

(deftest parse-price-test
  (testing "Converte formato brasileiro com centavos"
    (is (= 1250.90 (parse-price "R$ 1.250,90")))
    (is (= 12.90 (parse-price "12,90"))))

  (testing "Converte formato de milhares exato (Pegadinha do ML)"
    (is (= 1650.0 (parse-price "1.650")))
    (is (= 2900.0 (parse-price "2.900"))))

  (testing "Converte formato inteiro ou americano"
    (is (= 51.77 (parse-price "51.77")))
    (is (= 189.0 (parse-price "189"))))

  (testing "Lida com strings vazias e lixo de forma segura"
    (is (nil? (parse-price "")))
    (is (nil? (parse-price nil)))
    (is (nil? (parse-price "apenas texto sem numero")))))

(deftest parse-installment-test
  (testing "Extrai multiplicador e valor da parcela corretamente"
    (is (= [10 665.6] (parse-installment "em 10x de R$ 66,56 sem juros")))
    (is (= [3 150.0] (parse-installment "3x 50,00"))))

  (testing "Usa fallback para parse normal se não tiver 'x'"
    (is (= 165.0 (parse-installment "165,00")))))

(deftest get-selectors-test
  (testing "Identifica a loja correta pela URL"
    (is (= "Mercado Livre" (:site_name (scraper/get-selectors "https://produto.mercadolivre.com.br/MLB-123"))))
    (is (= "Amazon" (:site_name (scraper/get-selectors "https://www.amazon.com.br/dp/B000"))))
    (is (= "Magazine Luiza" (:site_name (scraper/get-selectors "https://www.magazineluiza.com.br/produto")))))

  (testing "Retorna nil para lojas não cadastradas"
    (is (nil? (scraper/get-selectors "https://www.loja-desconhecida.com.br/produto")))))

(deftest fetch-product-data-test
  (let [mock-html "<html>
                     <body>
                       <h1 data-testid='heading'>Produto de Teste</h1>
                       <div data-testid='price-original'>R$ 1.000,00</div>
                       <p data-testid='price-value'><span class='sr-only'>R$ 800,00</span></p>
                       <div data-testid='price-installment'>10x 80,00</div>
                     </body>
                   </html>"
        mock-selectors {:site_name "Loja Mock"
                        :name "h1[data-testid='heading']:not(.hidden)"
                        :price_original "[data-testid='price-original']"
                        :price_cash "[data-testid='price-value'] .sr-only"
                        :price_installment "[data-testid='price-installment']"}
        mock-url "https://www.magazineluiza.com.br/mock"]

    (testing "Extrai dados com sucesso do HTML fornecido"
      ;; Sequestramos o clj-http para retornar nosso HTML falso em vez de ir na internet
      (with-redefs [client/get (fn [_ _] {:body mock-html})]
        (let [result (scraper/fetch-product-data mock-url mock-selectors)]
          (is (= "Produto de Teste" (:product_name result)))
          (is (= 1000.0 (:price_original result)))
          (is (= 800.0 (:price_cash result)))
          (is (= 800.0 (:price_installment result)))
          (is (= "Loja Mock" (:site_name result))))))

    (testing "Retorna nil se ocorrer uma Exception na requisição (Timeout/Bloqueio)"
      (with-redefs [client/get (fn [_ _] (throw (Exception. "Timeout simulado")))]
        (is (nil? (scraper/fetch-product-data mock-url mock-selectors)))))))