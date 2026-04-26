(ns preco-historico.scraper
  (:require [clj-http.client :as client]
            [clojure.string :as str])
  (:import [org.jsoup Jsoup]))


(defn- parse-price
  "Transforma 'R$ 1.250,90', '£51.77' ou '€ 10,00' em Double"
  [price-str]
  (when-not (str/blank? price-str)
    (try
      (let [;;Qualquer coisa que NÃO (^) seja dígito (\d), ponto ou vírgula
            clean-str (str/replace price-str #"[^\d.,]" "")]
        (cond (str/includes? clean-str ",")
              (-> clean-str
                  (str/replace #"\." "") ;; Remove o ponto de milhar
                  (str/replace #"," ".") ;; Transforma a vírgula decimal em ponto
                  (Double/parseDouble))
              (re-matches #"^\d{1,3}(\.\d{3})+$" clean-str)
              (-> clean-str
                  (clojure.string/replace #"\." "")
                  (Double/parseDouble))
              :else
              (Double/parseDouble clean-str)))
      (catch Exception _ nil)))) ;; Se vier lixo, não quebra a app, retorna nil

(defn- extract-text
  "Encapsula a chamada do Jsoup. Retorna o texto do elemento ou nil se não achar."
  [doc selector]
  (when-let [element (.first (.select doc selector))]
    (.text element)))

(defn- extract-attr
  "Encapsula a extração de atributos (ex: pegar o link de uma imagem)."
  [doc selector attr]
  (when-let [element (.first (.select doc selector))]
    (.attr element attr)))

(defn- parse-installment
  "Tenta extrair o padrão '12x 390,74' e retorna o valor total multiplicado. 
   Se não achar o 'x', cai para o parse normal."
  [installment-str]
  (when-not (clojure.string/blank? installment-str)
    (try
      ;; Usamos uma regex para capturar (Grupo 1)x(Grupo 2)
      ;; Ex: "12x R$ 390,74" -> G1="12", G2="390,74"
      (let [match (re-find #"(?i)(\d+)\s*x[^\d]*([\d.,]+)" installment-str)]
        (if match
          (let [parcelas (Integer/parseInt (nth match 1))
                valor-parcela (parse-price (nth match 2))]
            (tap> {:parcelas parcelas
                   :stringa installment-str})
            (when valor-parcela
              [parcelas
               (/ (Math/round (* parcelas valor-parcela 100.0)) 100.0)]))
          (parse-price installment-str)))
      (catch Exception _ nil))))

;; O nosso dicionário de inteligência de sites
(def store-selectors
  {"mercadolivre.com.br" {:site_name "Mercado Livre"
                          :name ".ui-pdp-title"
                          :price_original ".ui-pdp-price__original-value .andes-money-amount__fraction"
                          :price_cash ".ui-pdp-price__second-line .andes-money-amount__fraction"
                          :price_installment ".ui-pdp-price__subtitles"}
   "amazon.com.br"       {:site_name "Amazon"
                          :name "#productTitle"
                          :price_original "#corePriceDisplay_desktop_feature_div .a-price.a-text-price.apex-basisprice-value span.a-offscreen"
                          :price_cash ".a-price-whole"
                          :price_installment ".best-offer-name"}
   "magazineluiza.com.br" {:site_name "Magazine Luiza"
                           :name "h1[data-testid='heading']:not(.hidden)"
                           :price_original "[data-testid='price-original']"
                           :price_cash "[data-testid='price-value'] .sr-only"
                           :price_installment "[data-testid='price-installment'] .sr-only"}})

(defn get-selectors
  "Descobre qual é a loja pela URL e retorna os seletores corretos."
  [url]
  (when url
    (cond
      (clojure.string/includes? url "mercadolivre.com.br") (get store-selectors "mercadolivre.com.br")
      (clojure.string/includes? url "amazon.com.br")       (get store-selectors "amazon.com.br")
      (clojure.string/includes? url "magazineluiza.com.br") (get store-selectors "magazineluiza.com.br")
      :else nil)))

(defn fetch-product-data
  "Baixa a URL e extrai nome e múltiplos preços usando um mapa de seletores CSS"
  [url {:keys [site_name name price_original price_cash price_installment]}]
  (try
    (let [response (client/get url {:headers {"User-Agent" "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
                                    :socket-timeout 5000
                                    :connection-timeout 5000
                                    :cookie-policy :ignoreCookies})
          doc (Jsoup/parse (:body response))
          product-name    (extract-text doc name)
          raw-original    (extract-text doc price_original)
          raw-cash        (extract-text doc price_cash)
          raw-installment (extract-text doc price_installment)
          [parcelas price_installment] (parse-installment raw-installment)]
      (tap> {:raw-original raw-original
             :raw-cash raw-cash
             :raw-installment price_installment
             :max-installments parcelas})
      {:url url
       :product_name product-name
       :price_original (parse-price raw-original)
       :price_cash (parse-price raw-cash)
       :price_installment (or price_installment 0.0)
       :site_name (or site_name "Site Desconhecido")
       :max_installments (or parcelas 0)})
    (catch Exception e
      (println "Erro ao raspar a URL:" url "-" (ex-message e))
      nil)))