(defproject frand "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.cemerick/friend "0.2.1"]
                 [friend-oauth2 "0.1.1"]
                 [hiccup "1.0.5"]
                 [compojure "1.2.0"]
                 [ring "1.3.1"]
                 [http-kit "2.1.19"]]
  :target-path "target/%s"
  :repl-options {:init-ns frand.core})
