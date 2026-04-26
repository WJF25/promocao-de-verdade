(ns preco-historico.auth-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as st]
            [buddy.sign.jwt :as jwt]
            [preco-historico.auth :as auth]
            [tick.core :as t]))

(deftest hash-password-test
  (testing "hash-password retorna uma string de hash (bcrypt) diferente da senha pura"
    (let [plain "super-secret"
          hashed (auth/hash-password plain)]
      (is (string? hashed))
      (is (not= plain hashed))
      (is (re-find #"^bcrypt\+sha512\$" hashed)))))

(deftest check-password-test
  (testing "check-password valida senha correta e rejeita senha incorreta"
    (let [plain "super-secret"
          hashed (auth/hash-password plain)]
      (is (true? (auth/check-password plain hashed)))
      (is (false? (auth/check-password "wrong-password" hashed))))))

(deftest jwt-token-test
  (let [secret "chave-de-teste-muito-segura"
        user {:id #uuid "aebe98b4-9860-4f2c-97cd-bc35c77f2070"
              :email "usuario@teste.com"}]

    (testing "create-token gera uma string JWT válida"
      (let [token (auth/create-token user secret)]
        (is (string? token))
        ;; O token JWT sempre tem 3 partes separadas por pontos
        (is (= 3 (count (st/split token #"\."))))))

    (testing "unsign-token recupera os dados corretamente com a chave certa"
      (let [token (auth/create-token user secret)
            payload (auth/unsign-token token secret)]
        (is (= (str (:id user)) (:user_id payload))) ;; UUID vira String no JSON
        (is (= (:email user) (:email payload)))
        (is (contains? payload :exp))))

    (testing "unsign-token retorna nil para uma chave secreta errada"
      (let [token (auth/create-token user secret)
            payload (auth/unsign-token token "chave-errada-de-vilao")]
        (is (nil? payload))))

    (testing "unsign-token retorna nil para um token adulterado"
      (let [token (auth/create-token user secret)
            tampered-token (str token "adulterado")]
        (is (nil? (auth/unsign-token tampered-token secret)))))))

(deftest jwt-expiration-test
  (let [secret "chave-secreta"
        user {:id #uuid "aebe98b4-9860-4f2c-97cd-bc35c77f2070"
              :email "usuario@teste.com"}]

    (testing "unsign-token retorna nil para um token expirado"
      (let [;; Criamos um tempo de expiração que foi há 1 hora atrás
            expired-at (-> (t/now)
                           (t/<< (t/new-duration 1 :hours))
                           (t/inst))

            ;; Criamos o payload manualmente com essa data retroativa
            payload {:user_id (:id user)
                     :email   (:email user)
                     :exp     expired-at}

            ;; Assinamos o token (simulando um token que expirou)
            expired-token (jwt/sign payload secret)]

        ;; O nosso unsign-token deve capturar a exceção de expiração e retornar nil
        (is (nil? (auth/unsign-token expired-token secret)))))))