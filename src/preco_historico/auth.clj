(ns preco-historico.auth
  (:require
   [buddy.hashers :as hashers]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as st]
   [tick.core :as t]))

(def ^:private bcrypt-alg :bcrypt+sha512)

(def ^:private trusted-algs #{bcrypt-alg})

(defn hash-password
  "Recebe uma senha em texto puro e retorna um hash (bcrypt)."
  [plain-password]
  (hashers/derive plain-password {:alg bcrypt-alg}))

(defn check-password [plain password-hash]
  (if (st/blank? password-hash)
    false
    (try
      (-> (hashers/verify plain password-hash {:limit trusted-algs})
          :valid
          boolean)
      (catch Exception _ false))))

(defn- exp-time
  "Calcula o timestamp de expiração (24 horas a partir de agora)."
  []
  (-> (t/now)
      (t/>> (t/new-duration 24 :hours))
      (t/inst)))

(defn create-token
  "Gera um JWT assinado com os dados do usuário.
   O payload contém o user_id e o email."
  [user secret]
  (let [payload {:user_id (:id user)
                 :email   (:email user)
                 :exp     (exp-time)}]
    (jwt/sign payload secret)))

(defn unsign-token
  "Tenta validar um token recebido. 
   Retorna o payload se for válido, ou nil se estiver expirado/violado."
  [token secret]
  (try
    (jwt/unsign token secret)
    (catch Exception _ nil)))

;; TODO unsign-token por que o catch só tem nil, dá pra melhorar?