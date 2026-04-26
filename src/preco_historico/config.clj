(ns preco-historico.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- load-dot-env!
  "Lê o arquivo .env (se existir) e injeta nas propriedades do sistema Java.
   Isso permite que o Aero encontre as variáveis localmente."
  []
  (let [env-file (io/file ".env")]
    (when (.exists env-file)
      (doseq [line (str/split-lines (slurp env-file))
              :when (and (not (str/blank? line))
                         (not (str/starts-with? (str/trim line) "#")))]
        (let [[k v] (str/split line #"=" 2)]
          (System/setProperty (str/trim k) (str/trim v)))))))

(defn load-config
  "Carrega o config EDN com suporte a profile do Aero.
   Ex.: (load-config {:profile :test})"
  ([] (load-config {}))
  ([aero-opts]
   (load-dot-env!) ;; Chama a nossa injeção de ambiente
   (aero/read-config (io/resource "config.edn") aero-opts)))
