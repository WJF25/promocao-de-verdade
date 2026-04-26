(defproject preco-historico "0.1.0-SNAPSHOT"
  :description "Ecossistema de monitoramento de preços"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :pedantic? :abort
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [cheshire "5.11.0"]
                 [com.fasterxml.jackson.core/jackson-core "2.14.2"]
                 [com.fasterxml.jackson.core/jackson-databind "2.14.2"]
                 [com.fasterxml.jackson.core/jackson-annotations "2.14.2"]
                 [commons-codec "1.16.0"]
                 [commons-io "2.15.1"]
                 [org.clojure/tools.logging "1.2.1"]
                 [aero "1.1.6"]
                 [buddy/buddy-auth "3.0.323" :exclusions [cheshire]]
                 [buddy/buddy-hashers "2.0.167" :exclusions [cheshire commons-codec]]
                 [buddy/buddy-sign "3.5.351" :exclusions [cheshire commons-codec]]
                 [clj-http "3.12.3" :exclusions [commons-codec commons-io]]
                 [clojure.java-time "1.4.2"]
                 [com.github.seancorfield/next.jdbc "1.3.1048"]
                 [com.layerware/hugsql "0.5.3"]
                 [com.layerware/hugsql-adapter-next-jdbc "0.5.3" :exclusions [org.clojure/tools.logging]]
                 [djblue/portal "0.54.2"]
                 [jarohen/chime "0.3.3" :exclusions [org.clojure/tools.logging]]
                 [lein-cloverage "1.2.4"]
                 [metosin/malli "0.13.0"]
                 [metosin/reitit "0.6.0" :exclusions [commons-io]]
                 [migratus "1.6.3" :exclusions [org.clojure/data.json org.clojure/tools.logging]]
                 [org.postgresql/postgresql "42.7.8"]
                 [org.jsoup/jsoup "1.17.2"]
                 [ring/ring-core "1.12.1" :exclusions [commons-io]]
                 [ring/ring-jetty-adapter "1.12.1" :exclusions [commons-io]]
                 [ring/ring-json "0.5.1" :exclusions [cheshire]]
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
