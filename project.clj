(defproject preco-historico "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[aero "1.1.6"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-hashers "2.0.167"]
                 [buddy/buddy-sign "3.5.351"]
                 [chime "0.3.3"]
                 [clj-http "3.12.3"]
                 [com.github.seancorfield/next.jdbc "1.3.1048"]
                 [com.layerware/hugsql "0.5.3"]
                 [com.layerware/hugsql-adapter-next-jdbc "0.5.3"]
                 [djblue/portal "0.54.2"]
                 [lein-cloverage "1.2.4"]
                 [metosin/malli "0.13.0"]
                 [metosin/reitit "0.6.0"]
                 [migratus "1.6.3"]
                 [org.clojure/clojure "1.11.1"]
                 [org.postgresql/postgresql "42.7.8"]
                 [org.jsoup/jsoup "1.17.2"]
                 [ring/ring-core "1.12.1"]
                 [ring/ring-jetty-adapter "1.12.1"]
                 [ring/ring-json "0.5.1"]
                 [tick "0.7.5"]]
  :main ^:skip-aot preco-historico.core
  :target-path "target/%s"
  :resource-paths ["resources"]
  :plugins [[lein-cloverage "1.2.2"]]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[nrepl/nrepl "1.1.1"]
                                  [org.clojure/tools.namespace "1.4.2"]]
                   :plugins [[cider/cider-nrepl "0.47.0"]]
                   :repl-options {:port 7888
                                  :host "0.0.0.0"}
                   :source-paths ["src" "dev"]}
             :test {:dependencies [[ring/ring-mock "0.4.0"]]}}
  :cloverage {:ns-exclude-regex [#"preco-historico\.core"
                                 #"preco-historico\.config"
                                 #"preco-historico\.server"
                                 #"user"]})
