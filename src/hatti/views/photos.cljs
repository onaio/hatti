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
            [milia.utils.images :refer [resize-image]]
            [milia.utils.remote :as remote]))

(def data-pswp-id "data-pswp-id")
(def pswp-gallery-class "pswp")
(def width-px 800)
(def num-columns 3)

(defn- make-url
  "Remove the API namespace prefix from a URI string and convert to a fully
   qualified URL."
  [str]
  (remote/make-url (replace str #"/api/v1" "")))

(defn- open-photoswipe
  "Initiate photoswipe using the index of the image that was just clicked on."
  [index owner]
  (let [pswp-element (first (.querySelectorAll js/document
                                               (str "." pswp-gallery-class)))
        options {:index (int index)
                 :getThumbBoundsFn
                 (fn [index]
                   (let [thumbnail
                         (first (.querySelectorAll
                                 js/document
                                 (format "[%s='%s']"
                                         data-pswp-id
                                         index)))
                         page-y-scroll (+ (.-pageYOffset js/window)
                                          (.. js/document
                                              -documentElement
                                              -scrollTop))
                         rect (.getBoundingClientRect thumbnail)]
                     {:x (.-left rect)
                      :y (+ (.-top rect) page-y-scroll)
                      :w (.-width rect)}))}
        gallery (js/PhotoSwipe. pswp-element
                                js/PhotoSwipeUI_Default
                                (clj->js (om/get-state owner :photos))
                                (clj->js options))]
    (.init gallery)))

(defn- on-thumbnail-click
  "Actions to perform on thumbnail click."
  [event owner]
  (.preventDefault event)
  (open-photoswipe (.getAttribute (.-target event) data-pswp-id)
                   owner))

(defn- build-photos
  "Build photos for photoswipe from a set of form data."
  [data]
  (flatten
   (for [datum data
         :let [attachments (get datum constants/_attachments)]
         :when (seq attachments)]
     (for [attachment attachments]
       {:src (resize-image
              (make-url (get attachment constants/download-url))
              width-px
              width-px)
        :thumb (make-url (get attachment constants/small-download-url))
        :rank (get datum constants/_rank)
        :w width-px
        :h width-px}))))

(defn- build-photo-gallery
  "Build markup with actions for a photo gallery."
  [photos owner]
  (map #(vector :tr %)
       (partition-all
        num-columns
        (for [i (-> photos count range)
              :let [photo (nth photos i)
                    caption (format "Submission %s" (:rank photo))]]
          [:td
           [:figure {"itemProp" "associatedMedia"
                     "itemScope" ""
                     "itemType" "http://schema.org/ImageObject"}
            [:a {:href (:src photo)
                 "itemProp" "contentUrl"
                 :data-size (format "%sx%s" width-px width-px)
                 :on-click #(on-thumbnail-click % owner)}
             [:img {:src (:thumb photo)
                    "itemProp" "thumbnail"
                    (keyword data-pswp-id) i
                    :alt caption}]]
            [:figcaption {"itemProp" "caption description"} caption]]]))))

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
        [:div {:role "dialog"
               :aria-hidden "true"
               :tab-index "-1"
               :class pswp-gallery-class}
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
        [:div.gallery {"itemScope" ""
                       "itemType" "http://schema.org/ImageGallery"}
         [:table
          (build-photo-gallery photos owner)]]]))))
