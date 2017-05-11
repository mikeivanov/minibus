(ns minibus.core
  (:require [clojure.core.async :as a :refer [<! >! <!!]]
            [clojure.stacktrace :refer [print-stack-trace]]))

;; Stateless "low-level" API

(defn make-bus* []
  (let [chan (a/chan)
        pub (a/pub chan first)]
    {:chan chan
     :pub pub
     :subs {}}))

(defn topics* [bus]
  (keys (:subs bus)))

(defn matches? [value x]
  (or (nil? value)
      (= value x)))

(defn subscriptions* [bus topic endpoint]
  (let [subs (mapcat (fn [[topic sub]]
                       (map vector
                            (repeat topic)
                            (keys sub)))
                     (:subs bus))]
    (filter (fn [[t e]]
              (and (matches? topic t)
                   (matches? endpoint e)))
            subs)))

(defn has-subscribers?* [bus topic]
  (< 0 (count (get-in bus [:subs topic]))))

(defn unsubscribe* [bus [topic endpoint]]
  (let [pub  (:pub bus)
        chan (get-in bus [:subs topic endpoint])]
    (when chan
      (a/unsub pub topic chan)
      (a/close! chan))
    (let [bus (update-in bus [:subs topic] dissoc endpoint)]
      (if (has-subscribers?* bus topic)
        bus
        (update-in bus [:subs] dissoc topic)))))

(defn unsubscribe-all* [bus topic endpoint]
  (reduce #(unsubscribe* %1 %2)
          bus
          (subscriptions* bus topic endpoint)))

(defn close* [bus]
  (when bus
    (let [bus (unsubscribe* bus [nil nil])
          {:keys [pub chan]} bus]
      (a/close! chan))
    nil))

(defn subscribe* [bus [topic endpoint] & {:keys [capacity type]
                                          :or   {capacity 1
                                                 type :sliding}}]
  {:pre [(#{:sliding :dropping} type)]}
  (let [bus  (unsubscribe* bus [topic endpoint])
        pub  (:pub bus)
        buf  (case type
               :sliding (a/sliding-buffer capacity)
               :dropping (a/dropping-buffer capacity))
        chan (a/chan buf)
        bus  (assoc-in bus
                       [:subs topic endpoint]
                       chan)]
    (a/sub pub topic chan)
    bus))

(defn channel* [bus [topic endpoint]]
  (get-in bus [:subs topic endpoint]))

(defn receive* [bus sub]
  (let [chan (channel* bus sub)]
    (assert (not (nil? chan)) "Subscription does not exist")
    (second (<!! chan))))

(defn publish* [bus topic message]
  (let [chan (:chan bus)]
    (a/go (>! chan [topic message])))
  bus)

;; A stateful "DSL" API

(defonce ^:dynamic bus (atom (make-bus*)))

(defn topics []
  (topics* @bus))

(defn subscriptions [& {:keys [topic endpoint]}]
  (subscriptions* @bus topic endpoint))

(defn has-subscribers? [topic]
  (has-subscribers?* @bus topic))

(defn make-endpoint []
  (str (java.util.UUID/randomUUID)))

(defn subscribe! [topic & {:keys [endpoint] :as options}]
  (let [endpoint (or endpoint (make-endpoint))]
    (swap! bus (fn [b]
                 (apply subscribe*
                        b [topic endpoint]
                        (mapcat vec options))))
    [topic endpoint]))

(defn channel [sub]
  (channel* @bus sub))

(defn receive! [sub]
  (receive* @bus sub))

(defn listen-channel [chan listener]
  (a/go-loop []
    (when-let [data (<! chan)]
      (listener data)
      (recur))))

(defn listen! [topic listener 
               & {:keys [on-error] :as options}]
  (let [sub (apply subscribe! topic (mapcat vec options))
        chan (channel sub)]
    (listen-channel chan (comp listener second))
    sub))

(defn unsubscribe! [sub]
  (swap! bus unsubscribe* sub)
  nil)

(defn unsubscribe-all! [& {:keys [topic endpoint]}]
  (swap! bus unsubscribe-all* topic endpoint)
  nil)

(defn publish! [topic message]
  (publish* @bus topic message)
  nil)

(defn start! []
  (when-not @bus
    (reset! bus (make-bus*))
    true))

(defn stop! []
  (swap! bus close*)
  nil)

(defn restart! []
  (stop!)
  (start!))

