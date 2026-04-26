(ns preco-historico.user
  (:require [preco-historico.auth :as auth]
            [preco-historico.db :as db]))

(defn create-user!
  "Cria um usuário com senha em texto puro (hash internamente) e retorna o registro persistido."
  [ds email plain-password]
  (let [password-hash (auth/hash-password plain-password)]
    (db/insert-user! ds {:email email :password_hash password-hash})))

(defn find-user-by-email [datasource email]
  ;; O HugSQL retorna uma lista, usamos o first para pegar o mapa do usuário
  (db/find-user-by-email datasource {:email email}))