(ns frand.core
  (:require [org.httpkit.server :as httpkit]
            [compojure.core :as compojure]
            [compojure.route :as route]

            [hiccup.core :as hiccup]

            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])

            [friend-oauth2.workflow :as oauth2]

            ring.middleware.params
            ring.middleware.keyword-params
            ring.middleware.nested-params
            ring.middleware.session

            clojure.pprint))

; a dummy in-memory user "database"
(def users {"root" {:username "root"
                    :password (creds/hash-bcrypt "admin_password")
                    :roles #{::admin}}
            "jane" {:username "jane"
                    :password (creds/hash-bcrypt "user_password")
                    :roles #{::user}}})

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
  (compojure/GET "/" [] (friend/authorize #{::admin} "Arthur is a doo"))
  (compojure/GET "/login" [] (hiccup/html login-form))
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
              (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn users)
                                    :workflows [(workflows/interactive-form)]})
              ((fn [handler]
                 (fn [req]
                   (clojure.pprint/pprint req)
                   (reset! before req)
                   (handler req))))
              ring.middleware.params/wrap-params
              ring.middleware.keyword-params/wrap-keyword-params
              ring.middleware.nested-params/wrap-nested-params
              ring.middleware.session/wrap-session
              (httpkit/run-server {:port 9090}))))

(defn restart-server
  []
  (stop-server)
  (require 'frand.core :reload)
  (start-server))
