(ns preco-historico.handler.user
  (:require [clojure.string :as string]
            [malli.core :as m]
            [malli.error :as me]
            [preco-historico.user :as user]))

(defn- duplicate-email-exception? [e]
  (let [msg (or (ex-message e) "")]
    (or (string/includes? msg "duplicate key value")
        (string/includes? msg "violates unique constraint"))))

(def UserBodySchema
  [:map {:closed true} [:email :string] [:password :string]])

(defn- validate-create-user-body [body]
  (if (m/validate UserBodySchema body)
    nil
    (let [erros (me/humanize (m/explain UserBodySchema body))]
      {:status 400 :body {:error "Dados inválidos" :detalhes erros}})))

(defn create-user-handler
  [datasource]
  (fn [request]
    (let [body (:body request)]
      (if-let [validation-error (validate-create-user-body body)]
        validation-error
        (try
          (let [result (user/create-user! datasource (:email body) (:password body))]
            {:status 201 :body result})
          (catch Exception e
            (if (duplicate-email-exception? e)
              {:status 409 :body {:error "E-mail já cadastrado"}}
              {:status 400 :body {:error "Erro ao criar usuário"}})))))))
