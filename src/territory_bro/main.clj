;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.main
  (:require [clojure.tools.logging :as log]
            [luminus.http-server :as http]
            [luminus.repl-server :as repl]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.projections :as projections]
            [territory-bro.router :as router])
  (:gen-class))

(mount/defstate ^{:on-reload :noop} http-server
  :start
  (http/start {:handler #'router/app
               :port (:port config/env)
               :io-threads (* 2 (.availableProcessors (Runtime/getRuntime)))})
  :stop
  (http/stop http-server))

(mount/defstate ^{:on-reload :noop} repl-server
  :start
  (when (:nrepl-port config/env)
    (repl/start {:bind (:nrepl-bind config/env)
                 :port (:nrepl-port config/env)}))
  :stop
  (when repl-server
    (repl/stop repl-server)))

(defn migrate-database! []
  (db/check-database-version 11)
  (let [master-schema (:database-schema config/env)]
    (log/info "Migrating master schema:" master-schema)
    (-> (db/master-schema master-schema)
        (.migrate))

    (db/with-db [conn {}]
      (projections/update-cache! conn))
    (doseq [congregation (congregation/get-unrestricted-congregations (projections/cached-state))]
      (let [tenant-schema (:congregation/schema-name congregation)]
        (log/info "Migrating tenant schema:" tenant-schema)
        (-> (db/tenant-schema tenant-schema master-schema)
            (.migrate))))))

(defn- log-mount-states [result]
  (doseq [component (:started result)]
    (log/info component "started"))
  (doseq [component (:stopped result)]
    (log/info component "stopped")))

(defn stop-app []
  (log-mount-states (mount/stop))
  (shutdown-agents))

(defn start-app []
  (try
    (log-mount-states (mount/start-without #'http-server))
    (migrate-database!)
    ;; start the public API only after the database is ready
    (log-mount-states (mount/start #'http-server))
    (doto (Runtime/getRuntime)
      (.addShutdownHook (Thread. ^Runnable stop-app)))
    (log/info "Started")
    (catch Throwable t
      (log/error t "Failed to start")
      (stop-app))))

(defn -main [& _args]
  (log-mount-states (mount/start #'config/env))
  (when (nil? (:database-url config/env))
    (log/error "Database configuration not found, :database-url must be set before running")
    (System/exit 1))
  (start-app))
