(ns katello.tests.content-views
  (:require (katello [conf :refer [*session-org*] :as conf]
                     [ui :as ui]
                     [rest :as rest]
                     [content-view-definitions :as views]
                     [notifications :as notifications]
                     [organizations :as organization]
                     [providers :as provider]
                     [repositories :as repo]
                     [tasks :refer :all]
                     [ui-common :as common]
                     [validation :refer :all])
            [test.tree.script :refer :all]
            [katello :as kt]
            [katello.tests.useful :refer [fresh-repo create-recursive]]
            [katello :refer [newOrganization newProvider newProduct newRepository newContentView]]
            [bugzilla.checker :refer [open-bz-bugs]]))

;; Functions

;; Tests



(defgroup cv-tests
  (deftest "Remove complete product or a repo from content-view-defnition"  
     (with-unique [org (kt/newOrganization {:name "auto-org"})
                   published-name ["pub-name1" "pub-name2"] 
                   cv (kt/newContentView {:name "composite-view"
                                          :org org 
                                          :description "Composite Content View" 
                                          :published-names [published-name]})
                   modified-name "mod-name"]
  
       (let [repo (fresh-repo org "http://repos.fedorapeople.org/repos/pulp/pulp/v2/stable/6Server/")]
         (ui/create-all (list org (kt/provider repo) (kt/product repo) repo cv)
         (ui/update cv assoc :name "mod-name123" :description "modified description")               
         ;;(ui/update (ui/update cv assoc :content repo) dissoc :content))))))
;;or
         (-> cv (ui/update assoc :content repo) (ui/update dissoc :content)))))))
