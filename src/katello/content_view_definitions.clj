(ns katello.content-view-definitions
  (:require [katello :as kt]
            [com.redhat.qe.auto.selenium.selenium :as sel :refer [browser]]
            [clojure.data :as data]
            (katello [navigation :as nav]
                     [rest :as rest]
                     [notifications :as notification]
                     [system-groups :as sg]
                     [tasks :refer [when-some-let] :as tasks]
                     [ui-common :as common]
                     [ui :as ui])))

;; Locators

(sel/template-fns
 {product-or-repository       "//li[contains(text(), '%s')]"
  composite-view-name         "//td[@class='view_checkbox' and contains(., '%s')]/input"
  ;;remove-products
  remove-repository           "//div[@class='repo' and contains(., '%s')]/a"})

(ui/deflocators
  {::new                      "new"
   ::name-text                "content_view_definition[name]"
   ::label-text               "katello/content_view_definition/default_label"
   ::description-text         "content_view_definition[description]"
   ::composite                "content_view_definition[composite]"
   ::save-new                 "commit"
   ::remove                   (ui/link "Remove")
   ::clone                    (ui/link "Clone")

   ::views-tab                 "//li[@id='view_definition_views']/a"
   ::content-tab               "//li[@id='view_definition_content']/a"
   ::filter-tab                "//li[@id='view_definition_filter']/a"
   ::details-tab               "//li[@id='view_definition_details']/a"
   ::update-content            "update_products"

   ;; Details tab
   ::details-name-text         "view_definition[name]"
   ::details-description-text  "view_definition[description]"


   ::sel-products              "window.$(\"#product_select_chzn\").mousedown()"
   ::sel-repo                  "//div/input[@class='product_radio' and @value='sel']"
   ::add-product-btn           "add_product"
   ::add-repo                  "//a[@class='add_repo']" 
   ::update-component_view     "update_component_views"
   ::remove-product            "//a[@class='remove_product']"
   ::remove-repo               "//a[@class='remove_repo']"
   ::toggle-products           "//div[@class='small_col toggle collapsed']"
;;toggle-products needs to help select which toggle-products, there could be many.
 ;;  I ned to find the right locator
   
   ;; Promotion
   ::publish-button            "//input[@type='button']"
   ::publish-name-text         "content_view[name]"
   ::publish-description-text  "content_view[description]"
   ::publish-new               "commit"
   ::refresh-button            "refresh_action"
   })

;; Nav
(nav/defpages (common/pages)
  [::page
   [::new-page [] (browser click ::new)]
   [::named-page [cv] (nav/choose-left-pane (:name cv))
    [::details-page [] (browser click ::details-tab)]]])

;; Tasks

(defn create
  "Creates a new Content View Definition."
  [{:keys [name description published-names org]}]
  (nav/go-to ::new-page {:org org})
  (sel/fill-ajax-form {::name-text name
                       ::description-text description
                       (fn [published-names] 
                         (when (not (nil?  published-names))
                           (browser click ::composite)
                           (doseq [publish-name published-names]
                             (browser click (composite-view-name publish-name))))) [published-names]}
                      ::save-new)
  (notification/check-for-success))
  

(defn update
  "Edits an existing Content View Definition."
  [{:keys [name description published-names org] :as cv}  updated]
  (let [[to-remove to-add _] (data/diff cv updated)]
    
  ;;Adds Products and Repositories to a content-view  
    (when (:content to-add)
      (nav/go-to cv)
      ;;(browser click ::content-tab)
      (browser getEval ::sel-products)
  ;; Composite Content Views are made up of other published views...
      (if (not (nil? (:published-names to-add)))
        (do
          (doseq [publish-name published-names]
            (sel/->browser  (click (composite-view-name publish-name))
                            (click ::update-component_view))))
  ;; Non-composite Content Views are made up of products and/or repositories.
        (do
          (sel/->browser (mouseUp (-> (:content to-add) :name product-or-repository))
                         (click ::add-product-btn)
                         (click ::update-content))))
      (notification/check-for-success))
    
    (when (not (nil? (:published-names to-add)))
      (browser click ::views-tab)
      (browser click ::publish-button)
      (doseq [publish-name published-names]
        (sel/fill-ajax-form {::publish-name-text publish-name
                             ::publish-description-text description}
                            ::publish-new))
      (notification/check-for-success {:timeout-ms (* 20 60 1000)}))
    
   ;; Need to combine products and repository section, but how do we explicitly tell
    ;;which block to enter for product and repo.
   ;; meant for removing product 
    (when (:content to-remove)
      (nav/go-to cv)
      (browser click ::content-tab)
      (sel/->browser  (click ::remove-product)
                      (click ::update-content))
      (notification/check-for-success))
   
   ;; meant for removing repository 
    (when (:content to-remove)
      (nav/go-to cv)
      (browser click ::content-tab)
      (sel/->browser   (click ::toggle-products)
                       (click (remove-repository  (-> (:content to-remove) :name)))
                       (click ::update-content))
      (notification/check-for-success))
    
   ;;Updating the details page.
    (when (or (:name to-add) (:description to-add))
      (nav/go-to cv)
      (browser click ::details-tab)
      (common/in-place-edit {::details-name-text (:name to-add)
                             ::details-description-text (:description to-add)})
      (notification/check-for-success))))

(defn delete
  "Deletes an existing View Definition."
  [cv]
  (nav/go-to cv)
  (browser click ::remove)
  (browser click ::ui/confirmation-yes)
  (notification/check-for-success))

(defn clone
  "Clones a content-view definition, given the name of the original definition
   to clone, and the new name and description."
  [cv clone]
  (nav/go-to cv)
  (browser click ::clone)
  (sel/fill-ajax-form {::sg/copy-name-text (:name clone)
                       ::sg/copy-description-text (:description clone)}
                      ::sg/copy-submit)
  (notification/check-for-success))

(extend katello.ContentView
  ui/CRUD {:create create
           :delete delete
           :update* update}
    
  tasks/Uniqueable tasks/entity-uniqueable-impl
  nav/Destination {:go-to (fn [cv] (nav/go-to ::named-page {:org (kt/org cv)
                                                            :cv cv}))})

