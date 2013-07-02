(ns katello.subscriptions
  (:require [clojure.java.io :as io]
            [com.redhat.qe.auto.selenium.selenium :as sel :refer [browser]]
            [katello :as kt]
            (katello [ui :as ui]
                     [rest :as rest]
                     [navigation :as nav]
                     [tasks :refer [with-unique]]
                     [conf :refer [config with-org]]
                     [tasks :as tasks]
                     [notifications :as notification]
                     [ui-common :as common]
                     [manifest :as manifest]
                     [sync-management :as sync]
                     [rh-repositories :as rh-repos])))

;; Locators

(ui/defelements :katello.deployment/any []
  {::new                      "new"
   ::upload-manifest          "upload_form_button"
   ::refresh-manifest         "refresh_form_button"
   ::create                   "commit"
   ::repository-url-text      "provider[repository_url]"
   ::choose-file              "provider_contents"
   ::fetch-history-info       "//td/span/span[contains(@class,'check_icon') or contains(@class, 'shield_icon')]"})

;; Nav

(nav/defpages :katello.deployment/any katello.menu
  [::page
   [::new-page (nav/browser-fn (click ::new))]
   [::named-page (fn [subscription] (nav/choose-left-pane subscription))
    [::details-page (nav/browser-fn (click ::details))]
    [::products-page (nav/browser-fn (click ::products))]
    [::units-page (nav/browser-fn (click ::products))]]]
  [::import-history-page])  

;; Tasks

(declare local-clone-source)

(defn download-original-once [redhat-manifest?]
  (defonce local-clone-source (let [dest (manifest/new-tmp-loc)
                                    manifest-details (if redhat-manifest? {:manifest-url (@config :redhat-manifest-url)
                                                                           :repo-url     (@config :redhat-repo-url)}
                                                                          {:manifest-url (@config :fake-manifest-url)
                                                                           :repo-url     (@config :fake-repo-url)})]
                                (io/copy (-> manifest-details :manifest-url java.net.URL. io/input-stream)
                                         (java.io.File. dest))
                                (kt/newManifest {:file-path dest
                                                 :url (manifest-details :repo-url)
                                                 :provider kt/red-hat-provider}))))

(defn prepare-org
  "Clones a manifest, uploads it to the given org (via api), and then
   enables and syncs the given repos"
  [repos]
  (let [manifest (do (when-not (bound? #'local-clone-source)
                               (download-original-once (-> repos first :reposet)))
                             local-clone-source) ]
    (rest/create (assoc manifest :provider (-> repos first kt/provider )))
    (when (-> repos first :reposet)
      (rh-repos/enable-disable-redhat-repos repos))
    (sync/perform-sync repos)))

(defn setup-org [envs repos]
  "Adds org to all the repos in the list, creates org and the envs
   chains"
  (let [org (-> envs first :org)
        repos (for [r repos]
                (if (-> repos first :reposet)
                  (update-in r [:reposet :product :provider] assoc :org org)
                  (update-in r [:product :provider] assoc :org org)))]
    (rest/create org)
    (doseq [e (kt/chain envs)]
      (rest/create e))
    (prepare-org repos)))


(defn- upload-manifest
  "Uploads a subscription manifest from the filesystem local to the
   selenium browser. Optionally specify a new repository url for Red
   Hat content- if not specified, the default url is kept. Optionally
   specify whether to force the upload."
  [{:keys [file-path url org]}]
  {:pre [(instance? katello.Organization org)]}
  (nav/go-to ::new-page org)
  (when-not (browser isElementPresent ::choose-file)
    (browser click ::new))
  (when url
    (common/in-place-edit {::repository-url-text url})
    (notification/success-type :prov-update))
  (sel/fill-ajax-form {::choose-file file-path}
                      ::upload-manifest)
  (browser refresh)
  ;;now the page seems to refresh on its own, but sometimes the ajax count
  ;; does not update. 
  ;; was using asynchronous notification until the bug https://bugzilla.redhat.com/show_bug.cgi?id=842325 gets fixed.
  (notification/check-for-success {:timeout-ms (* 30 60 1000)}))

(defn upload-new-cloned-manifest
  "Clones the manifest at orig-file-path and uploads it to the current org."
  [{:keys [file-path url] :as m}]
  (let [clone-loc (manifest/new-tmp-loc)
        clone (assoc m :file-path clone-loc)]
    (manifest/clone file-path clone-loc)
    (upload-manifest clone)))

(defn upload-manifest-import-history?
  "Returns true if after an manifest import the history is updated."
  []
  (nav/go-to ::import-history-page)
  (browser isElementPresent ::fetch-history-info))
  
(extend katello.Manifest
  ui/CRUD {:create upload-manifest}
  rest/CRUD {:create (fn [{:keys [url file-path] :as m}]
                       (merge m
                              (let [provid (-> m :provider rest/get-id)]
                                (do (rest/http-put (rest/api-url (format "/api/providers/%s" provid))
                                                   {:body {:provider {:repository_url url}}})
                                    (rest/http-post (rest/api-url (format "/api/providers/%s/import_manifest" provid))
                                                    {:multipart [{:name "import"
                                                                  :content (clojure.java.io/file file-path)
                                                                  :mime-type "application/zip"
                                                                  :encoding "UTF-8"}]})))))}
  tasks/Uniqueable {:uniques (fn [m]
                               (repeatedly (fn [] (let [newpath (manifest/new-tmp-loc)]
                                                    (manifest/clone (:file-path m) newpath)
                                                    (assoc m :file-path newpath)))))})

