;; Copyright © 2015-2018 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [territory-bro.permissions :as perm]))

(defn- format-tenant [id]
  ; TODO: human-readable name
  {:id id
   :name (.toUpperCase (name id))})

(defn my-congregations []
  (->> (perm/visible-congregations)
       (map format-tenant)
       (sort-by #(.toUpperCase (:name %)))))