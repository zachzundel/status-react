(ns status-im.hardwallet.core
  (:require [re-frame.core :as re-frame]
            [status-im.react-native.js-dependencies :as js-dependencies]
            [status-im.ui.screens.navigation :as navigation]
            [status-im.utils.config :as config]
            [status-im.utils.fx :as fx]
            [status-im.utils.platform :as platform]
            [taoensso.timbre :as log]
            [status-im.native-module.core :as status]
            [status-im.utils.types :as types]
            [status-im.constants :as constants]
            [status-im.utils.identicon :as identicon]
            [status-im.utils.gfycat.core :as gfycat]
            [status-im.data-store.accounts :as accounts-store]
            [status-im.utils.hex :as utils.hex]
            [clojure.string :as string]))

(defn check-nfc-support []
  (when config/hardwallet-enabled?
    (.. js-dependencies/nfc-manager
        -default
        isSupported
        (then #(re-frame/dispatch [:hardwallet.callback/check-nfc-support-success %])))))

(defn check-nfc-enabled []
  (when platform/android?
    (.. js-dependencies/nfc-manager
        -default
        isEnabled
        (then #(re-frame/dispatch [:hardwallet.callback/check-nfc-enabled-success %])))))

(fx/defn set-nfc-support
  [{:keys [db]} supported?]
  {:db (assoc-in db [:hardwallet :nfc-supported?] supported?)})

(fx/defn set-nfc-enabled
  [{:keys [db]} enabled?]
  {:db (assoc-in db [:hardwallet :nfc-enabled?] enabled?)})

(defn open-nfc-settings []
  (when platform/android?
    (.. js-dependencies/nfc-manager
        -default
        goToNfcSetting)))

(fx/defn navigate-to-connect-screen [cofx]
  (fx/merge cofx
            {:hardwallet/check-nfc-enabled  nil
             :hardwallet/register-tag-event nil}
            (navigation/navigate-to-cofx :hardwallet-connect nil)))

(defn hardwallet-supported? [db]
  (and config/hardwallet-enabled?
       platform/android?
       (get-in db [:hardwallet :nfc-supported?])))

(fx/defn return-back-from-nfc-settings [{:keys [db]}]
  (when (= :hardwallet-connect (:view-id db))
    {:hardwallet/check-nfc-enabled nil}))

(defn- proceed-to-pin-confirmation [fx]
  (assoc-in fx [:db :hardwallet :pin :enter-step] :confirmation))

(defn- pin-match [{:keys [db]}]
  {:db (assoc-in db [:hardwallet :pin :status] :validating)
   :utils/dispatch-later [{:ms 3000
                           :dispatch [:hardwallet.callback/on-pin-validated]}]})

(defn- pin-mismatch [fx]
  (assoc-in fx [:db :hardwallet :pin] {:status       :error
                                       :error        :t/pin-mismatch
                                       :original     []
                                       :confirmation []
                                       :enter-step   :original}))

(fx/defn process-pin-input
  [{:keys [db]} number enter-step]
  (let [db' (update-in db [:hardwallet :pin enter-step] conj number)
        numbers-entered (count (get-in db' [:hardwallet :pin enter-step]))]
    (cond-> {:db (assoc-in db' [:hardwallet :pin :status] nil)}
      (and (= enter-step :original)
           (= 6 numbers-entered))
      (proceed-to-pin-confirmation)

      (and (= enter-step :confirmation)
           (= (get-in db' [:hardwallet :pin :original])
              (get-in db' [:hardwallet :pin :confirmation])))
      (pin-match)

      (and (= enter-step :confirmation)
           (= 6 numbers-entered)
           (not= (get-in db' [:hardwallet :pin :original])
                 (get-in db' [:hardwallet :pin :confirmation])))
      (pin-mismatch))))

(defn- register-tag-event []
  (when config/hardwallet-enabled?
    (.. js-dependencies/nfc-manager
        -default
        (registerTagEvent #(re-frame/dispatch [:hardwallet.callback/on-tag-discovered %]))
        (then #(log/debug "[hardwallet] register tag event")))))

(defn- unregister-tag-event []
  (when config/hardwallet-enabled?
    (.. js-dependencies/nfc-manager
        -default
        (unregisterTagEvent)
        (then #(log/debug "[hardwallet] unregister tag event")))))

(fx/defn on-tag-discovered [{:keys [db] :as cofx} data]
  (let [data' (js->clj data :keywordize-keys true)
        payload (get-in data' [:ndefMessage 0 :payload])]
    (log/debug "[hardwallet] on tag discovered" data')
    (log/debug "[hardwallet] " (str "tag payload: " (clojure.string/join
                                                     (map js/String.fromCharCode payload))))
    (fx/merge cofx
              {:db (assoc-in db [:hardwallet :setup-step] :begin)}
              (navigation/navigate-to-cofx :hardwallet-setup nil))))

(fx/defn on-initialization-completed [{:keys [db]}]
  {:db (assoc-in db [:hardwallet :setup-step] :secret-keys)})

(fx/defn on-pairing-completed [{:keys [db]}]
  {:db (assoc-in db [:hardwallet :setup-step] :card-ready)})

(defn create-account [password]
  (status/create-account
   password
   #(re-frame/dispatch [:hardwallet.callback/create-account-success (types/json->clj %) password])))

(fx/defn on-pin-validated [{:keys [db] :as cofx}]
  (let [pin (get-in db [:hardwallet :pin :original])
        password (apply str pin)]
    (fx/merge cofx
              {:db (-> db
                       (assoc-in [:hardwallet :setup-step] :recovery-phrase))
               :hardwallet/create-account password})))

(fx/defn add-account
  "Takes db and new account, creates map of effects describing adding account to database and realm"
  [cofx {:keys [address] :as account}]
  (let [db (:db cofx)
        {:networks/keys [networks]} db
        enriched-account (assoc account
                                :network config/default-network
                                :networks networks
                                :address address)]
    {:db                 (assoc-in db [:accounts/accounts address] enriched-account)
     :data-store/base-tx [(accounts-store/save-account-tx enriched-account)]}))

(fx/defn on-account-created
  [{:keys [random-guid-generator
           signing-phrase
           status
           db] :as cofx}
   {:keys [pubkey address mnemonic]} password seed-backed-up]
  (let [normalized-address (utils.hex/normalize-hex address)
        account {:public-key             pubkey
                 :installation-id        (random-guid-generator)
                 :address                normalized-address
                 :name                   (gfycat/generate-gfy pubkey)
                 :status                 status
                 :signed-up?             true
                 :desktop-notifications? false
                 :photo-path             (identicon/identicon pubkey)
                 :signing-phrase         signing-phrase
                 :seed-backed-up?        seed-backed-up
                 :mnemonic               mnemonic
                 :settings               (constants/default-account-settings)}]
    (log/debug "account-created")
    (when-not (string/blank? pubkey)
      (fx/merge cofx
                {:db (assoc db :accounts/login {:address normalized-address
                                                :password password
                                                :processing true})}
                (add-account account)))))

(fx/defn on-create-account-success [{:keys [db] :as cofx} result password]
  (fx/merge cofx
            {:db (assoc-in db [:hardwallet :setup-step] :recovery-phrase)}
            (on-account-created result password false)))

(re-frame/reg-fx
 :hardwallet/check-nfc-support
 check-nfc-support)

(re-frame/reg-fx
 :hardwallet/check-nfc-enabled
 check-nfc-enabled)

(re-frame/reg-fx
 :hardwallet/open-nfc-settings
 open-nfc-settings)

(re-frame/reg-fx
 :hardwallet/register-tag-event
 register-tag-event)

(re-frame/reg-fx
 :hardwallet/unregister-tag-event
 unregister-tag-event)

(re-frame/reg-fx
 :hardwallet/create-account
 create-account)
