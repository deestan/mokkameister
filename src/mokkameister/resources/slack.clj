(ns mokkameister.resources.slack
  "Incoming webhooks from slack"
  (:require [liberator.core :refer [defresource]]
            [mokkameister.slack :as slack]
            [mokkameister.db.persistence :refer [persist-brew! brew-stats]]
            [mokkameister.system :refer [system]]
            [mokkameister.util :refer [parse-int]]))

(def ^:private msg-coffee-count
  {0 "Dagens fyrste kaffi! "
   1 "No er det snart kaffi igjen, "
   2 "Kaffi no igjen? "
   3 "Eg gir meg ende øve, fjerde kaffien i dag? "
   4 "Femte gong? "})

(def ^:private mokkameister-link
  "<https://mokkameister.herokuapp.com/|Mokkameister>")

(defn- coffee-message-starting [{:keys [slack-user brew-time]} today-count]
  (format "God nyhendnad folket! %s%s starta nett traktaren, kaffi om %d minuttar! - %s"
          (msg-coffee-count today-count "") slack-user brew-time mokkameister-link))

(defn- coffee-message-finished [{:keys [slack-user]}]
  (format "Det er kaffi å få på kjøken! @%s" slack-user))

(defn- coffee-message-instant [{:keys [slack-user]}]
  (format "Den sleipe robusta-knaskaren %s har lagt seg ein snar-kaffi :/" slack-user))


(defmulti handle-slack-coffee :coffee-type)

(defmethod handle-slack-coffee :instant [{:keys [channel] :as event}]
  (persist-brew! event)
  (let [msg (coffee-message-instant event)]
    (slack/notify msg :channel channel)))

(defmethod handle-slack-coffee :regular [{:keys [channel time-ms] :as event}]
  (let [today-count (get-in (brew-stats (system :db)) [:regular :today])
        now-msg     (coffee-message-starting event today-count)
        later-msg   (coffee-message-finished event)]
    (persist-brew! event)
    (slack/notify now-msg :channel channel)
    (slack/delayed-notify time-ms later-msg :channel channel)))


(defn parse-slack-coffee-event [{:keys [channel_id text user_name]}]
  (let [event {:channel    channel_id
               :slack-user user_name}]
    (if (= text "instant")
      (assoc event :coffee-type :instant, :brew-time 0)
      (let [time    (or (parse-int text) 5)
            time-ms (* time 1000 60)]
        (assoc event :coffee-type :regular, :brew-time time, :time-ms time-ms)))))

(defn valid-slack-token? [ctx]
  (let [token (get-in ctx [:request :params :token])]
    (= token (system :slack-token))))

(defresource slack-coffee
  :available-media-types ["text/plain"]
  :allowed-methods [:post]
  :authorized? valid-slack-token?
  :post! (fn [{{:keys [params]} :request}]
           (-> params
               parse-slack-coffee-event
               handle-slack-coffee) ""))