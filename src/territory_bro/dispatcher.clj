;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.dispatcher
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [territory-bro.commands :as commands]
            [territory-bro.config :as config]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.db-admin :as db-admin]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events]
            [territory-bro.foreign-key :as foreign-key]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.gis-user :as gis-user]
            [territory-bro.user :as user]))

(defn- default-injections [command state]
  {:now (:now config/env)
   :check-permit #(commands/check-permit state command %)})

(defn- reference-checkers [conn state]
  {:congregation (fn [cong-id]
                   (congregation/check-congregation-exists state cong-id)
                   true)
   :user (fn [user-id]
           (user/check-user-exists conn user-id)
           true)
   :new (fn [stream-id]
          (event-store/check-new-stream conn stream-id)
          true)})

(defn- validate-command [command conn state]
  (binding [foreign-key/*reference-checkers* (reference-checkers conn state)]
    (commands/validate-command command)))

(defn- write-stream! [conn stream-id f]
  (let [old-events (event-store/read-stream conn stream-id)
        new-events (f old-events)]
    (event-store/save! conn stream-id (count old-events) new-events)))

(defn- call! [command-handler command state-or-old-events injections]
  (->> (command-handler command state-or-old-events injections)
       (events/enrich-events command injections)
       (map events/sorted-keys)
       (events/strict-validate-events)))


(defn- congregation-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :generate-tenant-schema-name (fn [cong-id]
                                                         (db/generate-tenant-schema-name conn cong-id)))]
    (write-stream! conn
                   (:congregation/id command)
                   (fn [old-events]
                     (call! congregation/handle-command command old-events injections)))))

(defn- gis-user-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :generate-password #(gis-user/generate-password 50)
                          :db-user-exists? #(gis-db/user-exists? conn %))]
    (write-stream! conn
                   (:congregation/id command) ; TODO: the GIS user events would belong better to a user-specific stream
                   (fn [old-events]
                     (call! gis-user/handle-command command old-events injections)))))

(defn- db-admin-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :migrate-tenant-schema! db/migrate-tenant-schema!
                          :ensure-gis-user-present! (fn [args]
                                                      (gis-db/ensure-user-present! conn args))
                          :ensure-gis-user-absent! (fn [args]
                                                     (gis-db/ensure-user-absent! conn args)))]
    (call! db-admin/handle-command command state injections)))

(defn- pretty-str [object]
  (str/trimr (with-out-str
               (print "\n")
               (pprint/pprint object))))

(defn command! [conn state command]
  (let [command (-> command
                    (commands/sorted-keys)
                    (validate-command conn state))
        _ (log/info "Dispatch command:" (pretty-str command))
        events (case (namespace (:command/type command))
                 "congregation.command" (congregation-command! conn command state)
                 "gis-user.command" (gis-user-command! conn command state)
                 "db-admin.command" (db-admin-command! conn command state)
                 (throw (AssertionError. (str "Command handler not found: " (pr-str command)))))]
    (log/info "Produced events:" (pretty-str events))
    events))
