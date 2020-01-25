;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.territory-test
  (:require [clojure.test :refer :all]
            [territory-bro.events :as events]
            [territory-bro.territory :as territory]
            [territory-bro.testdata :as testdata]
            [territory-bro.testutil :as testutil])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException)))

(def cong-id (UUID. 0 1))
(def territory-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def territory-defined {:event/type :territory.event/territory-defined
                        :event/version 1
                        :congregation/id cong-id
                        :territory/id territory-id
                        :territory/number "123"
                        :territory/addresses "the addresses"
                        :territory/subregion "the subregion"
                        :territory/meta {:foo "bar"}
                        :territory/location testdata/wkt-multi-polygon})
(def territory-deleted {:event/type :territory.event/territory-deleted
                        :event/version 1
                        :congregation/id cong-id
                        :territory/id territory-id})

(defn- apply-events [events]
  (testutil/apply-events territory/projection events))

(defn- handle-command [command events injections]
  (->> (territory/handle-command (testutil/validate-command command)
                                 (events/validate-events events)
                                 injections)
       (events/validate-events)))

(deftest territory-projection-test
  (testing "created"
    (let [events [territory-defined]
          expected {::territory/territories
                    {cong-id {territory-id {:territory/id territory-id
                                            :territory/number "123"
                                            :territory/addresses "the addresses"
                                            :territory/subregion "the subregion"
                                            :territory/meta {:foo "bar"}
                                            :territory/location testdata/wkt-multi-polygon}}}}]
      (is (= expected (apply-events events)))

      (testing "> updated"
        (let [events (conj events (assoc territory-defined
                                         :territory/number "456"
                                         :territory/addresses "new addresses"
                                         :territory/subregion "new subregion"
                                         :territory/meta {:new-meta "new stuff"}
                                         :territory/location "new location"))
              expected (update-in expected [::territory/territories cong-id territory-id]
                                  merge {:territory/number "456"
                                         :territory/addresses "new addresses"
                                         :territory/subregion "new subregion"
                                         :territory/meta {:new-meta "new stuff"}
                                         :territory/location "new location"})]
          (is (= expected (apply-events events)))))

      (testing "> deleted"
        (let [events (conj events territory-deleted)
              expected {}]
          (is (= expected (apply-events events))))))))

(deftest create-territory-test
  (let [injections {:check-permit (fn [_permit])}
        create-command {:command/type :territory.command/create-territory
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :territory/id territory-id
                        :territory/number "123"
                        :territory/addresses "the addresses"
                        :territory/subregion "the subregion"
                        :territory/meta {:foo "bar"}
                        :territory/location testdata/wkt-multi-polygon}]

    (testing "created"
      (is (= [territory-defined]
             (handle-command create-command [] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command create-command [territory-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:create-territory cong-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command create-command [] injections)))))))

(deftest update-territory-test
  (let [injections {:check-permit (fn [_permit])}
        update-command {:command/type :territory.command/update-territory
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :territory/id territory-id
                        :territory/number "123"
                        :territory/addresses "the addresses"
                        :territory/subregion "the subregion"
                        :territory/meta {:foo "bar"}
                        :territory/location testdata/wkt-multi-polygon}]

    (testing "number changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/number "old number")] injections))))

    (testing "addresses changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/addresses "old addresses")] injections))))

    (testing "subregion changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/subregion "old subregion")] injections))))

    (testing "meta changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/meta {:stuff "old meta"})] injections))))

    (testing "location changed"
      (is (= [territory-defined]
             (handle-command update-command [(assoc territory-defined :territory/location "old location")] injections))))

    (testing "nothing changed / is idempotent"
      (is (empty? (handle-command update-command [territory-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:update-territory cong-id territory-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command update-command [territory-defined] injections)))))))

(deftest delete-territory-test
  (let [injections {:check-permit (fn [_permit])}
        delete-command {:command/type :territory.command/delete-territory
                        :command/time (Instant/now)
                        :command/user user-id
                        :congregation/id cong-id
                        :territory/id territory-id}]

    (testing "deleted"
      (is (= [territory-deleted]
             (handle-command delete-command [territory-defined] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command delete-command [territory-defined territory-deleted] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:delete-territory cong-id territory-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command delete-command [territory-defined] injections)))))))
  