;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.gis-db-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer :all]
            [territory-bro.congregation :as congregation]
            [territory-bro.db :as db]
            [territory-bro.fixtures :refer [db-fixture event-actor-fixture]]
            [territory-bro.gis-db :as gis-db]
            [territory-bro.projections :as projections]
            [territory-bro.territory :as territory]
            [territory-bro.testdata :as testdata]))

(use-fixtures :each (join-fixtures [db-fixture event-actor-fixture]))

(deftest regions-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "the name")
          _ (congregation/use-schema conn (projections/current-state conn) cong-id)]

      (testing "create & list congregation boundaries"
        (let [id (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon)]
          (is (= [{:region/id id
                   :region/location testdata/wkt-multi-polygon}]
                 (gis-db/get-congregation-boundaries conn)))))

      (testing "create & list subregions"
        (let [id (gis-db/create-subregion! conn "the name" testdata/wkt-multi-polygon)]
          (is (= [{:region/id id
                   :region/name "the name"
                   :region/location testdata/wkt-multi-polygon}]
                 (gis-db/get-subregions conn)))))

      (testing "create & list card minimap viewports"
        (let [id (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon)]
          (is (= [{:region/id id
                   :region/location testdata/wkt-polygon}]
                 (gis-db/get-card-minimap-viewports conn))))))))

(deftest gis-change-log-test
  (db/with-db [conn {}]
    (jdbc/db-set-rollback-only! conn)

    (let [cong-id (congregation/create-congregation! conn "the name")
          _ (congregation/use-schema conn (projections/current-state conn) cong-id)]

      (testing "before making changes"
        (is (= [] (gis-db/get-changes conn))))

      (testing "territory table change log,"
        (let [territory-id (territory/create-territory! conn {:territory/number "123"
                                                              :territory/addresses "Street 1 A"
                                                              :territory/subregion "Somewhere"
                                                              :territory/meta {:foo "bar", :gazonk 42}
                                                              :territory/location testdata/wkt-multi-polygon})]
          (testing "insert"
            (let [changes (gis-db/get-changes conn)]
              (is (= 1 (count changes)))
              (is (= {:table "territory"
                      :op "INSERT"
                      :old nil
                      :new {:id (str territory-id)
                            :number "123"
                            :addresses "Street 1 A"
                            :subregion "Somewhere"
                            :meta {:foo "bar", :gazonk 42}
                            :location testdata/wkt-multi-polygon}}
                     (-> (last changes)
                         (dissoc :id :schema :user :time))))))

          (testing "update"
            (jdbc/execute! conn ["UPDATE territory SET addresses = 'Another Street 2' WHERE id = ?" territory-id])
            (let [changes (gis-db/get-changes conn)]
              (is (= 2 (count changes)))
              (is (= {:table "territory"
                      :op "UPDATE"
                      :old {:id (str territory-id)
                            :number "123"
                            :addresses "Street 1 A"
                            :subregion "Somewhere"
                            :meta {:foo "bar", :gazonk 42}
                            :location testdata/wkt-multi-polygon}
                      :new {:id (str territory-id)
                            :number "123"
                            :addresses "Another Street 2"
                            :subregion "Somewhere"
                            :meta {:foo "bar", :gazonk 42}
                            :location testdata/wkt-multi-polygon}}
                     (-> (last changes)
                         (dissoc :id :schema :user :time))))))

          (testing "delete"
            (jdbc/execute! conn ["DELETE FROM territory WHERE id = ?" territory-id])
            (let [changes (gis-db/get-changes conn)]
              (is (= 3 (count changes)))
              (is (= {:table "territory"
                      :op "DELETE"
                      :old {:id (str territory-id)
                            :number "123"
                            :addresses "Another Street 2"
                            :subregion "Somewhere"
                            :meta {:foo "bar", :gazonk 42}
                            :location testdata/wkt-multi-polygon}
                      :new nil}
                     (-> (last changes)
                         (dissoc :id :schema :user :time))))))))

      (testing "congregation_boundary table change log"
        (let [region-id (gis-db/create-congregation-boundary! conn testdata/wkt-multi-polygon)
              changes (gis-db/get-changes conn)]
          (is (= 4 (count changes)))
          (is (= {:table "congregation_boundary"
                  :op "INSERT"
                  :old nil
                  :new {:id (str region-id)
                        :location testdata/wkt-multi-polygon}}
                 (-> (last changes)
                     (dissoc :id :schema :user :time))))))

      (testing "subregion table change log"
        (let [region-id (gis-db/create-subregion! conn "Somewhere" testdata/wkt-multi-polygon)
              changes (gis-db/get-changes conn)]
          (is (= 5 (count changes)))
          (is (= {:table "subregion"
                  :op "INSERT"
                  :old nil
                  :new {:id (str region-id)
                        :name "Somewhere"
                        :location testdata/wkt-multi-polygon}}
                 (-> (last changes)
                     (dissoc :id :schema :user :time))))))

      (testing "card_minimap_viewport table change log"
        (let [region-id (gis-db/create-card-minimap-viewport! conn testdata/wkt-polygon)
              changes (gis-db/get-changes conn)]
          (is (= 6 (count changes)))
          (is (= {:table "card_minimap_viewport"
                  :op "INSERT"
                  :old nil
                  :new {:id (str region-id)
                        :location testdata/wkt-polygon}}
                 (-> (last changes)
                     (dissoc :id :schema :user :time)))))))

    (testing "get changes since X"
      (let [all-changes (gis-db/get-changes conn)]
        (is (= all-changes
               (gis-db/get-changes conn {:since 0}))
            "since beginning")
        (is (= (drop 1 all-changes)
               (gis-db/get-changes conn {:since 1}))
            "since first change")
        (is (= (take-last 1 all-changes)
               (gis-db/get-changes conn {:since (:id (first (take-last 2 all-changes)))}))
            "since second last change")
        (is (= []
               (gis-db/get-changes conn {:since (:id (last all-changes))}))
            "since last change")
        ;; should probably be an error, but an empty result is safer than returning all events
        (is (= []
               (gis-db/get-changes conn {:since nil}))
            "since nil")))))