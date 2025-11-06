(ns anmo.schemas
  (:require [malli.core :as m])
  (:import (java.util.concurrent ExecutorCompletionService)))

(def ModsConf
  [:map
   [:game-id pos-int?]
   [:base-url :string]
   [:api-key :string]
   [:download-dir :string]
   [:mods-dir :string]])

(def ModsListRaw [:vector :string])
(def ModsList [:vector :keyword])

(def ModInfo
  [:map
   [:mod-name :string]
   [:mod-name-id :string]
   [:mod-id pos-int?]
   [:mod-file-name :string]
   [:mod-file-size pos-int?]
   [:mod-version :string]
   [:mod-download-url :string]])
(def ModsInfo
  [:map-of :keyword ModInfo])

(def LocalModInfo
  [:map-of
   :keyword
   [:map
    [:mod-version :string]
    [:is-disabled? :boolean]
    [:mod-path :string]]])

(def LocalDownloadModInfo
  [:map-of
   :keyword
   [:map
    [:mod-version :string]]])

(def -ExecutorCompletionService
  (m/-simple-schema
    {:type            :executor-completion-service
     :pred            #(instance? ExecutorCompletionService %)
     :type-properties {:error/message "should be instance of ExecutorCompletionService"}}))
