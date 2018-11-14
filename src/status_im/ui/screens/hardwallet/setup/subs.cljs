(ns status-im.ui.screens.hardwallet.setup.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 :hardwallet-setup-step
 (fn [db]
   (get-in db [:hardwallet :setup-step])))

(re-frame/reg-sub
 :hardwallet-pair-code
 (fn [db]
   (get-in db [:hardwallet :pair-code])))

(re-frame/reg-sub
 :hardwallet-recovery-phrase-word
 (fn [db]
   (get-in db [:hardwallet
               :recovery-phrase
               (get-in db [:hardwallet :recovery-phrase :step])])))

(re-frame/reg-sub
 :hardwallet-recovery-phrase-confirm-error
 (fn [db]
   (get-in db [:hardwallet :recovery-phrase :confirm-error])))

(re-frame/reg-sub
 :hardwallet-recovery-phrase-step
 (fn [db]
   (get-in db [:hardwallet :recovery-phrase :step])))
