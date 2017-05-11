(ns minibus.core-test
  (:require [clojure.test :refer :all]
            [minibus.core :refer :all]))

(deftest test-bus-lifecycle
  (testing "make-bus* should create a bus"
    (let [bus (make-bus*)]
      (is (not (nil? bus)))
      (is (not (nil? (:pub bus))))
      (is (not (nil? (:chan bus))))))
  (testing "close-bus* makes bus empty" 
    (let [bus (close* (make-bus*))]
      (is (= {} bus)))))

(deftest test-subscriptions
  (testing "subscription creates topics"
    (let [bus (-> (make-bus*)
                  (subscribe* [:topic1 :endpoint])
                  (subscribe* [:topic2 :endpoint]))]
      (is (= [:topic1 :topic2] (topics* bus))))))
