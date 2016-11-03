(ns hatti.views.photos
  (:require [chimera.js-interop :refer [format]]
            [cljsjs.photoswipe]
            [cljsjs.photoswipe-ui-default]
            [clojure.string :refer [replace]]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [hatti.constants :as constants]
            [hatti.shared :as shared]
            [hatti.views :refer [photos-page]]
            [chimera.urls :refer [url]]
            [milia.utils.remote :refer [thumbor-server]]
            [milia.utils.remote :as remote]))

(def data-pswp-id "data-pswp-id")
(def pswp-gallery-class "pswp")
(def width-px 1024)
(def thumb-width-px 180)
(def thumb-width-px-str (str thumb-width-px "px"))
(def num-columns 3)

(defn- resize-image
  "Return a URL for this image resized."
  [image-url edge-px]
  (str thumbor-server
       (url  "unsafe"
             "fit-in"
             (str edge-px "x" edge-px)
             "smart"
             "filters:fill(000)"
             image-url)))

(defn- make-url
  "If not a fully qualified URL, remove the API namespace prefix from a URI
   string and convert to a fully qualified URL."
  [s]
  (if (= (subs s 0 4) "http")
    s
    (remote/make-url (replace s #"/api/v1" ""))))

(defn- build-caption
  [item]
  (let [datetime (.-date item)
        date (-> datetime js/moment (.format "MMM D, YYYY"))
        time (-> datetime js/moment (.format "h:mm A"))]
    (format "%s <br/><small>Submitted at %s on %s <br/>Record ID: %s </small>"
            (.-title item) time date (.-id item))))

;;; We use strings for some keywords below because keywords are forced to
;;; lower-case.

(defn- open-photoswipe
  "Initiate photoswipe using the index of the image that was just clicked on."
  [index photos]
  (let [pswp-element (first (.querySelectorAll js/document
                                               (str "." pswp-gallery-class)))
        options {:index (int index)
                 "getThumbBoundsFn"
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
                     (clj->js {:x (.-left rect)
                               :y (+ (.-top rect) page-y-scroll)
                               :w (.-width rect)})))
                 "addCaptionHTMLFn"
                 (fn [item caption-el is-fake]
                   (set! (-> caption-el .-children first .-innerHTML)
                         (build-caption item)))}
        gallery (js/PhotoSwipe. pswp-element
                                js/PhotoSwipeUI_Default
                                (clj->js photos)
                                (clj->js options))]
    (.init gallery)))

(defn- on-thumbnail-click
  "Actions to perform on thumbnail click."
  [event photos]
  (.preventDefault event)
  (open-photoswipe (.getAttribute (.-target event) data-pswp-id)
                   photos))

(defn- image-or-no-mimetype?
  "Return True if no mimetype or the mimetype begins with image. Otherwise
   return nil."
  [attachment]
  (let [mimetype (get attachment constants/mimetype)]
    (or (not mimetype) (= "image" (subs mimetype 0 5)) nil)))

(defn extract-images
  "Take a datum and return the datum with a new key :attachements that has a
   list of download URLs for all the attachments."
  [datum photo-columns]
  (let [download-url-kw (keyword constants/download-url)
        attachments
        (or (some-> datum
                    (get constants/photo)
                    download-url-kw
                    vector)
            (reduce #(some->> (get datum %2)
                              download-url-kw
                              (conj %1)
                              (into []))
                    nil
                    photo-columns)
            (keep #(and
                    (image-or-no-mimetype? %)
                    (get % constants/download-url))
                  (get datum constants/_attachments)))]
    (and (seq attachments) (assoc datum :attachments attachments))))

(defn- build-photos
  "Build photos for photoswipe from a set of form data. Ignore submissions that
   do not have a photo attached. If the photo info is attached directly to the
   submission and not to the attachments list, use the information attached
   directly to the submission."
  [data photo-columns]
  (let [data-with-attachments (keep #(extract-images % photo-columns) data)
        total (reduce #(+ %1 (count (get %2 :attachments)))
                      0 data-with-attachments)]
    (loop [data-left data-with-attachments
           result []
           photo-index 1]
      (if (seq data-left)
        (let [{:keys [attachments] :as datum} (first data-left)
              rank (get datum constants/_rank)
              photo (get datum constants/photo)]
          (recur
           (rest data-left)
           (concat
            result
            (map-indexed
             (fn [j attachment]
               (let [download-url (make-url attachment)
                     thumbnail (resize-image download-url
                                             thumb-width-px)]
                 {:src (resize-image (make-url download-url) width-px)
                  :original-src download-url
                  :msrc thumbnail
                  :thumb thumbnail
                  :title (format "%s/%s | Submission %s"
                                 (+ photo-index j) total rank)
                  :date (get datum constants/_submission_time)
                  :id (get datum constants/_id)
                  :rank rank
                  :w width-px
                  :h width-px}))
             attachments))
           (+ photo-index (count attachments))))
        result))))

(defn- get-photo-columns
  "Return the full-names of all columns with photo type."
  [form]
  (keep #(and (or (= "photo" (:type %)) nil) (:full-name %)) form))

(defn- build-photo-gallery
  "Build markup with actions for a photo gallery."
  [photos owner]
  (for [i (-> photos count range)
        :let [{:keys [title] :as photo} (nth photos i)]]
    [:figure {"itemProp" "associatedMedia"
              "itemScope" ""
              "itemType" "http://schema.org/ImageObject"}
     [:a {:href (:src photo)
          "itemProp" "contentUrl"
          :data-size (format "%sx%s" width-px width-px)
          :on-click #(on-thumbnail-click % photos)}
      [:img {:width thumb-width-px-str
             :height thumb-width-px-str
             :src (:thumb photo)
             "itemProp" "thumbnail"
             (keyword data-pswp-id) i
             :alt title}]]
     [:figcaption {"itemProp" "caption description"} title]]))

(defmethod photos-page :default
  [{{:keys [num_of_submissions] :as dataset-info} :dataset-info} owner]
  "Om component for the photos page."
  (reify
    om/IInitState
    (init-state [_]
      ;; Use map-page data because it is not paged, like the table data
      (assoc
       (select-keys @shared/app-state [:map-page :data])
       :photo-columns (get-photo-columns (om/get-shared owner [:flat-form]))))
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (om/set-state! owner :data (-> @shared/app-state :map-page :data)))
    om/IRenderState
    (render-state [_ {:keys [data photo-columns]}]
      (let [form (om/get-shared owner [:flat-form])
            photos (build-photos data photo-columns)]
        (html
         [:div.tab-content
          (cond
            (or (zero? num_of_submissions)
                (and (seq data) (empty? photos)))
            [:p.alert.alert-warning.alert-photos
             "There are no photos in this dataset yet."]
            (seq photos)
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
               [:div.pswp__share-modal.pswp__share-modal--hidden
                {:class "pswp__single-tap"}
                [:div.pswp__share-tooltip]]
               [:button.pswp__button.pswp__button--arrow--left
                {:title "Previous (arrow left)"}]
               [:button.pswp__button.pswp__button--arrow--right
                {:title "Next (arrow right)"}]
               [:div.pswp__caption
                [:div.pswp__caption__center]]]]]
            :else
            [:p.alert.alert-photos
             [:i.fa.fa-spinner.fa-pulse] "Loading photos ..."])
          [:div.gallery {"itemScope" ""
                         "itemType" "http://schema.org/ImageGallery"}
           [:table
            (build-photo-gallery photos owner)]]])))))
