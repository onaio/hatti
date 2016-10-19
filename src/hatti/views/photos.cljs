(ns hatti.views.photos
  (:require [chimera.js-interop :refer [format]]
            [cljsjs.photoswipe]
            [cljsjs.photoswipe-ui-default]
            [clojure.string :refer [replace]]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.constants :as constants]
            [hatti.shared :as hatti-shared]
            [hatti.views :refer [photos-page]]
            [milia.utils.remote :as remote]))

(def width-px 400)

(defn- make-url
  "Remove the API namespace prefix from a URI string and convert to a fully
   qualified URL."
  [str]
  (remote/make-url (replace str #"/api/v1" "")))

(defn build-photos
  "Build photos for photoswipe from a set of form data."
  [data]
  (flatten
   (for [datum data
         :let [attachments (get datum constants/_attachments)]
         :when (seq attachments)]
     (for [attachment attachments]
       {:src (make-url (get attachment constants/medium-download-url))
        :thumb (make-url (get attachment constants/small-download-url))
        :rank (get datum constants/_rank)
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
        [:div.gallery {:itemscope ""
                       :itemtype "http://schema.org/ImageGallery"}
         (for [photo photos
               :let [caption (format "Submission %s" (:rank photo))]]
           [:figure {:itemprop "associatedMedia"
                     :itemscope ""
                     :itemtype "http://schema.org/ImageObject"}
            [:a {:href (:src photo)
                 :itemprop "contentUrl"
                 :data-size (format "%sx%s" width-px width-px)}
             [:img {:src (:thumb photo)
                    :itemprop "thumbnail"
                    :alt caption}]]
            [:figcaption {:itemprop "caption description"} caption]])]]))
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
