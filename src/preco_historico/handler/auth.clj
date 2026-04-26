(ns preco-historico.handler.auth
  (:require [preco-historico.auth :as auth]
            [preco-historico.user :as user-db]))

(defn login-handler [datasource secret]
  (fn [request]
    (let [{:keys [email password]} (:body request)
          user (user-db/find-user-by-email datasource email)]

      (if (and user (auth/check-password password (:password_hash user)))
        ;; Sucesso: Geramos o token
        (let [token (auth/create-token user secret)]
          {:status 200
           :body {:token token
                  :user {:id (:id user) :email (:email user)}}})

        ;; Falha: Silêncio por segurança (401)
        {:status 401
         :body {:error "E-mail ou senha inválidos"}}))))