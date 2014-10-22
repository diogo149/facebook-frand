(ns frand.core
  (:require [org.httpkit.server :as httpkit]
            [compojure.core :as compojure]
            [compojure.route :as route]

            [hiccup.core :as hiccup]

            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])

            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :refer [format-config-uri get-access-token-from-params]]

            [clj-facebook-graph.client :as fb]
            [clj-facebook-graph.auth :as fbauth]

            ring.middleware.params
            ring.middleware.keyword-params
            ring.middleware.nested-params
            ring.middleware.session

            clojure.pprint))

;; NOTE: this port must be the same as in the facebook app's settings
(def port 9696)

; a dummy in-memory user "database"
(def users {"root" {:username "root"
                    :password (creds/hash-bcrypt "admin_password")
                    :roles #{::admin}}
            "jane" {:username "jane"
                    :password (creds/hash-bcrypt "user_password")
                    :roles #{::user}}})

;; --------------
;; facebook stuff
;; --------------

(defn credential-fn
  [token]
  (println "TOKEN:" token)
  ;; TODO error handling
  (let [fb-user (:body (fbauth/with-facebook-auth token
                         (fb/get [:me])))
        {:keys [id first_name last_name name]} fb-user]
    ;;lookup token in DB or whatever to fetch appropriate :roles
    {:identity token
     :roles #{::user}
     :fb-user fb-user
     :fb-id id
     :first-name first_name
     :last-name last_name
     :full-name name}))

(def client-config
  {:client-id "" ;; TODO fill me in
   :client-secret "" ;; TODO fill me in
   :callback {:domain (str "http://localhost:" port) :path "/facebook.callback"}})

(def uri-config
  {:authentication-uri {:url "https://www.facebook.com/dialog/oauth"
                        :query {:client_id (:client-id client-config)
                                :redirect_uri (format-config-uri client-config)}}

   :access-token-uri {:url "https://graph.facebook.com/oauth/access_token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :redirect_uri (format-config-uri client-config)}}})

;; ------
;; server
;; ------

(def server (atom nil))

(def login-form
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h3 "Login"]
    [:div {:class "row"}
     [:form {:method "POST" :action "login" :class "columns small-4"}
      [:div "Username" [:input {:type "text" :name "username"}]]
      [:div "Password" [:input {:type "password" :name "password"}]]
      [:div [:input {:type "submit" :class "button" :value "Login"}]]]]]])

(compojure/defroutes app
  (compojure/GET "/" [] "hello you doo")
  (compojure/GET "/user" [] (friend/authorize #{::user} "Arthur is a doo"))
  (compojure/GET "/login" [] (hiccup/html login-form))
  (compojure/GET "/fb" req
                 (friend/authorize #{::user}
                                   (let [i (friend/identity req)
                                         {:keys [current authentications]} i
                                         curr (authentications current)]
                                     (pr-str curr))))
  (route/not-found "Doo"))

(defn stop-server
  []
  (when-let [s @server]
    (s)
    (reset! server nil)))

(def before (atom nil))
(def after (atom nil))

(defn start-server
  []
  (stop-server)
  (reset! server
          (-> app
              ((fn [handler]
                 (fn [req]
                   (clojure.pprint/pprint req)
                   (reset! after req)
                   (handler req))))
              ;; normal friend authentication
              #_
              (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn users)
                                    :workflows [(workflows/interactive-form)]})
              ;; facebook authentication
              (friend/authenticate
               {:allow-anon? true
                :workflows [(oauth2/workflow
                             {:client-config client-config
                              :uri-config uri-config
                              :access-token-parsefn get-access-token-from-params
                              :credential-fn credential-fn})]})
              ((fn [handler]
                 (fn [req]
                   (clojure.pprint/pprint req)
                   (reset! before req)
                   (handler req))))
              ;; REMEMBER, put this after the others!!! :(
              ring.middleware.keyword-params/wrap-keyword-params
              ring.middleware.params/wrap-params
              ring.middleware.nested-params/wrap-nested-params
              ring.middleware.session/wrap-session
              (httpkit/run-server {:port port}))))

(defn restart-server
  []
  (stop-server)
  (require 'frand.core :reload)
  (start-server))
