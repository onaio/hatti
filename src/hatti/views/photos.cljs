(ns hatti.views.photos
  (:require [cljsjs.photoswipe]
            [cljsjs.photoswipe-ui-default]
            [clojure.string :refer [replace]]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.ona.post-process :refer [attachments-key]]
            [hatti.shared :as hatti-shared]
            [hatti.views :refer [photos-page]]
            [milia.utils.remote :refer [make-url]]))

(def medium-download-url "medium_download_url")
(def width-px 400)

(defn build-photos
  "Build photos for photoswipe from a set of form data."
  [data]
  (flatten
   (for [datum data
         :let [attachments (get datum attachments-key)]
         :when (seq attachments)]
     (for [attachment attachments]
       {:src (make-url (replace (get attachment medium-download-url)
                                #"/api/v1" ""))
        :w width-px :h width-px}))))

(defmethod photos-page :default
  [{:keys [dataset-info]} owner]
  "Om component for the photos page."
  (reify
    om/IInitState
    (init-state [_]
      {:photos (build-photos (:data @hatti-shared/app-state))})
    om/IRenderState
    (render-state [_ {:keys [photos]}]
      (html
       [:div.container
        [:div.pswp {:role "dialog" :aria-hidden "true" :tab-index "-1"}
         [:div.pswp__bg]
         [:div.pswp__scroll-wrap
          [:div.pswp__container
           [:div.pswp__item]
           [:div.pswp__item]
           [:div.pswp__item]]
          [:div.pswp__ui.pswp__ui--hidden
           [:div.pswp__top-bar
            [:div.pswp__counter]
            [:button.pswp__button.pswp__button--close
             {:title "Close (Esc)"}]
            [:button.pswp__button.pswp__button--share
             {:title "Share"}]
            [:button.pswp__button.pswp__button--fs
             {:title "Toggle fullscreen"}]
            [:button.pswp__button.pswp__button--zoom
             {:title "Zoom in/out"}]
            [:div.pswp__preloader
             [:div.pswp__preloader__icn
              [:div.pswp__preloader__cut
               [:div.pswp__preloader__donut]]]]]
           [:div.pswp__share-modal.pswp__share-modal--hidden.pswp__single-tap
            [:div.pswp__share-tooltip]]
           [:button.pswp__button.pswp__button--arrow--left
            {:title "Previous (arrow left)"}]
           [:button.pswp__button.pswp__button--arrow--right
            {:title "Next (arrow right)"}]
           [:div.pswp__caption
            [:div.pswp__caption__center]]]]]
        ]))
    om/IDidMount
    (did-mount [_]
      (let [pswp-element (first (.querySelectorAll js/document ".pswp"))
            options {:index 0 ;; start at the first slide
                     }
            gallery (js/PhotoSwipe. pswp-element
                                    js/PhotoSwipeUI_Default
                                    (clj->js (om/get-state owner :photos))
                                    (clj->js options))]
        (.init gallery)))))
