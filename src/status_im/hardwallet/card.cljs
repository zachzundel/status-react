(ns status-im.hardwallet.card
  (:require [re-frame.core :as re-frame]
            [status-im.react-native.js-dependencies :as js-dependencies]
            [status-im.utils.config :as config]
            [status-im.utils.platform :as platform]
            [taoensso.timbre :as log]))

(defonce keycard (.-default js-dependencies/status-keycard))
(defonce event-emitter (.-DeviceEventEmitter js-dependencies/react-native))

(defn check-nfc-support []
  (when config/hardwallet-enabled?
    (.nfcIsSupported
     keycard
     #(re-frame/dispatch [:hardwallet.callback/check-nfc-support-success %]))))

(defn check-nfc-enabled []
  (when platform/android?
    (.nfcIsEnabled
     keycard
     #(re-frame/dispatch [:hardwallet.callback/check-nfc-enabled-success %]))))

(defn open-nfc-settings []
  (when platform/android?
    (.openNfcSettings keycard #())))

(defn start []
  (when config/hardwallet-enabled?
    (.start keycard #() #())))

(defn initialize []
  (when config/hardwallet-enabled?
    (.init keycard
           #(re-frame/dispatch [:hardwallet.callback/on-initialization-success %])
           #(re-frame/dispatch [:hardwallet.callback/on-initialization-error %]))))

(defn register-tag-event []
  (when config/hardwallet-enabled?
    (.addListener event-emitter
                  "keyCardOnConnected"
                  #(re-frame/dispatch [:hardwallet.callback/on-tag-discovered %]))

    (.addListener event-emitter
                  "keyCardOnDisconnected"
                  #(log/debug "[hardwallet] card disconnected"))))

(defn pair [cofx]
  (let [pairing-password (get-in cofx [:db :hardwallet :secrets :password])]
    (when pairing-password
      (.pair keycard
             pairing-password
             #(re-frame/dispatch [:hardwallet.callback/on-pairing-success %])
             #(re-frame/dispatch [:hardwallet.callback/on-pairing-error %])))))

(defn generate-mnemonic [cofx]
  (let [{:keys [password]} (get-in cofx [:db :hardwallet :secrets])]
    (when password
      (.generateMnemonic keycard
                         password
                         #(re-frame/dispatch [:hardwallet.callback/on-generate-mnemonic-success %])
                         #(re-frame/dispatch [:hardwallet.callback/on-generate-mnemonic-error %])))))

(defn save-mnemonic [cofx]
  (let [{:keys [mnemonic password pin]} (get-in cofx [:db :hardwallet :secrets])]
    (when password
      (.saveMnemonic keycard
                     mnemonic
                     password
                     pin
                     #(re-frame/dispatch [:hardwallet.callback/on-save-mnemonic-success %])
                     #(re-frame/dispatch [:hardwallet.callback/on-save-mnemonic-error %])))))

(defn get-application-info []
  (.getApplicationInfo keycard
                       #(re-frame/dispatch [:hardwallet.callback/get-application-info-success %])
                       #(re-frame/dispatch [:hardwallet.callback/get-application-info-error %])))

