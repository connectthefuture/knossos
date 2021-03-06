(ns knossos.linear-test
  (:require [clojure.test :refer :all]
            [knossos.linear :refer :all]
            [knossos.op :refer :all]
            [knossos.model :refer [cas-register register multi-register
                                   inconsistent]]
            [knossos.core-test :as ct]
            [clojure.pprint :refer [pprint]]))

(deftest bad-analysis-test
  (let [history [{:process 0 :type :invoke :f :read :value 1}
                 {:process 0 :type :ok     :f :read :value 1}]]
    (is (= {:valid? false
            :previous-ok nil
            :last-op nil
            :op {:process 0
                 :index   1
                 :type    :ok
                 :f       :read
                 :value   1}
            :configs [{:model (register 0)
                       :last-op nil
                       :pending [{:process  0
                                  :index    0
                                  :type     :invoke
                                  :f        :read
                                  :value    1}]}]
            :final-paths #{[{:model (register 0) :op nil}
                            {:model (inconsistent
                                      "0≠1")
                             :op {:process 0
                                  :index 1
                                  :type :ok
                                  :f :read
                                  :value 1}}]}}
           (analysis (register 0) history)))))

(deftest bad-analysis-test-2
  (let [a (analysis (cas-register 0) (ct/read-history-2 "data/cas-register/bad/cas-failure.edn"))]
    ; In this particular history, we know the value is 0, then we have
    ; concurrent reads of 0 and a write of 2 by process 76, followed by another
    ; read of 0 by process 70. The only legal linearization to that final read
    ; is all reads of 0, followed by 76 write 2, which leaves the state as 2.
    ; Process 70 read 0 should be the invalidating op.
    (is (= false (:valid? a)))
    ;; This is the only possible state at this time.
    (is (= [{:model (cas-register 2)
             :last-op {:f :write, :index 472, :process 76, :type :ok, :value 2}
             :pending
             [{:process 70, :type :invoke, :f :read, :value 0, :index 488}
              {:process 77, :type :invoke, :f :cas, :value [1 1], :index 463}]}]
           (:configs a)))
    ; We fail because we can't linearize the final read of 0
    (is (= {:process 70, :type :ok, :f :read, :value 0, :index 491}
           (:op a)))
    ; The last linearized ok was the completion of process 74's read of 0, but
    ; that's not the last linearized *op*: that'd be the write of 2.
    (is (= {:process 74, :type :ok, :f :read, :value 0, :index 478}
           (:previous-ok a)))
    (is (= {:process 76, :type :ok, :f :write, :value 2, :index 472}
           (:last-op a)))

    ; There are two possible options: write 2, CAS 1->1, or write 2, read 0.
    (is (= #{[{:model (cas-register 2)
               :op {:f :write :index 472 :process 76 :type :ok :value 2}}
              {:model (inconsistent "can't CAS 2 from 1 to 1")
               :op {:f :cas :index 463 :process 77 :type :invoke :value [1 1]}}]
             [{:model (cas-register 2)
               :op {:f :write :index 472 :process 76 :type :ok :value 2}}
              {:model (inconsistent "can't read 0 from register 2")
               :op {:f :read :index 491 :process 70 :type :ok :value 0}}]}
           (:final-paths a)))))

(deftest volatile-linearizable-test
  (dotimes [i 10]
    (let [history (ct/volatile-history 100 50 1/1000)
          _       (prn (count history))
          a       (analysis (register 0) history)]
      (is (:valid? a))
      (when (not= true (:valid? a))
;        (pprint history)
        (println "history length" (count history))
        (prn)
        (pprint (assoc a :configs (take 2 (:configs a))))))))

(deftest multi-register-test
  (let [a (analysis (multi-register {:x 0 :y 0})
                    (ct/read-history-2 "data/multi-register/good/multi-register.edn"))]
    (is (:valid? a))
    (is (= #{(multi-register {:x 2 :y 2})
             (multi-register {:x 2 :y 0})}
           (set (map :model (:configs a)))))))

(deftest example-test
  (ct/test-examples analysis))
