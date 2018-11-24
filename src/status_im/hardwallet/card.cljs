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
    (.. keycard
        nfcIsSupported
        (then #(re-frame/dispatch [:hardwallet.callback/check-nfc-support-success %])))))

(defn check-nfc-enabled []
  (when platform/android?
    (.. keycard
        nfcIsEnabled
        (then #(re-frame/dispatch [:hardwallet.callback/check-nfc-enabled-success %])))))

(defn open-nfc-settings []
  (when platform/android?
    (.openNfcSettings keycard #())))

(defn start []
  (when config/hardwallet-enabled?
    (.. keycard
        start
        (then #(log/debug "[hardwallet] module started"))
        (catch #(log/debug "[hardwallet] module not started " %)))))

(defn initialize []
  (when config/hardwallet-enabled?
    (.. keycard
        init
        (then #(re-frame/dispatch [:hardwallet.callback/on-initialization-success %]))
        (catch #(re-frame/dispatch [:hardwallet.callback/on-initialization-error (str %)])))))

(defn register-card-events []
  (when config/hardwallet-enabled?
    (.addListener event-emitter
                  "keyCardOnConnected"
                  #(re-frame/dispatch [:hardwallet.callback/on-card-connected %]))

    (.addListener event-emitter
                  "keyCardOnDisconnected"
                  #(re-frame/dispatch [:hardwallet.callback/on-card-disconnected %]))))

(defn pair [cofx]
  (let [pairing-password (get-in cofx [:db :hardwallet :secrets :password])]
    (when pairing-password
      (.. keycard
          (pair pairing-password)
          (then #(re-frame/dispatch [:hardwallet.callback/on-pairing-success %]))
          (catch #(re-frame/dispatch [:hardwallet.callback/on-pairing-error (str %)]))))))

(defn generate-mnemonic [cofx]
  (let [{:keys [password]} (get-in cofx [:db :hardwallet :secrets])]
    (when password
      (.. keycard
          (generateMnemonic password)
          (then #(re-frame/dispatch [:hardwallet.callback/on-generate-mnemonic-success %]))
          (catch #(re-frame/dispatch [:hardwallet.callback/on-generate-mnemonic-error (str %)]))))))

(defn save-mnemonic [cofx]
  (let [{:keys [mnemonic password pin]} (get-in cofx [:db :hardwallet :secrets])]
    (when password
      (.. keycard
          (saveMnemonic mnemonic password pin)
          (then #(re-frame/dispatch [:hardwallet.callback/on-save-mnemonic-success %]))
          (catch #(re-frame/dispatch [:hardwallet.callback/on-save-mnemonic-error (str %)]))))))

(defn get-application-info []
  (.. keycard
      getApplicationInfo
      (then #(re-frame/dispatch [:hardwallet.callback/get-application-info-success %]))
      (catch #(re-frame/dispatch [:hardwallet.callback/get-application-info-error (str %)]))))

