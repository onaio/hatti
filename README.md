[![Build Status](https://travis-ci.org/onaio/hatti.svg?branch=master)](https://travis-ci.org/onaio/hatti)

# hatti

A cljs dataview from [Ona](http://beta.ona.io).

## Overview

This library is a work-in-progress to package up much of the dataview used in Ona's new product. It provides a set of Om components that can be used to visualize a dataset that has a schema attached to it.

## Terminology used below:

 * `data` is a vector of `records`
 * `flat-form` is a vector of `fields`
 * Each `field` is a map, containing keys `name`, `type`, `full-name`, `label` at the very least.
 * `fields` with type repeats are special; we will not go into detail about them for now.
 * Each `record` is also a map. Each key in a `record` should correspond to the `full-name` of some `field`.

## General overview of state, shared-state, etc.

`app-state` has the following structure:
```clj
   {:data []
   :map-page {:submission-clicked {:data nil}
              :geofield {}}
    :table-page {:submission-clicked {:data nil}}
    :chart-page {:visible-charts []
                 :chart-data {}}
    :dataset-info {}
    :languages {:current nil :all []}}
```

The meaning of the state is as follows:

 * `:data` should be all of the data for this dataset. A list of records, each record being a submission.
 * `:dataset-info` is what is available via the `/forms/` endpoint. Inside here, there should be things like form `metadata`, `num_of_submissions` and `instances_with_geopoints`, `enketo_url`, `enketo_preview_url` and such.
 * `languages` stores the set of languages labels in this form are written in inside of `:all`, the currently selected language (default English) is store in :current. This part of the app-state is used by the reference cursor language-cursor.
 * `:map-page`, `:table-page`, and `:chart-page` contain the state necessary for each of those pages.
   * `:submission-clicked` stores data about the record which is clicked.
   * `visible-charts` is the list of all charts that the user has called up on this page. `chart-data` is a map that stores the raw chart API data for all of the fields that the user has requested charts on.

User has the responsibility of updating `:data`, `:dataset-info` and `:flat-form` by fetching them; the utility `flatten-form` is available to convert what you get from `Ona` into a *flat* form..

**Note**: for speed of decoding, it is assumed for now that the keys for `data` are plain strings, not keywords. `data` is exceptional in this way; other maps are expected to contain plain clojure(script) keywords.

`shared-state` needs:
```clj
  {:flat-form []
   :view-type :ona-default}
```
exposed `cursor`s:
```clj
shared/language-cursor
```

To render the dataview into your application, you'll need something like the following. The app-state can be updated once constructed, and generally the right thing will happen. Shared state cannot be updated.
```clj
(om/root tabbed-dataview
          app-state-atom
          {:target ...
           :shared {:flat-form _your-form_
                    :view-type _your-view-type_}})
```

(Future: `flat-form` will be moved to app-state, since it does need to be updated sometimes).

### Component Hierarchy

* `tabbed-dataview`
   * `dataview-infobar`
   * `map-page` (cursor: map-page, dataset-info)
     * `map-and-markers` (cursor: map-page)
     * `geofield-chooser` (cursor: map-page geo-field)
     * `view-by-legend` (cursor: map-page view-by, dataset-info)
       * `view-by-menu` (cursor: dataset-info)
       * `view-by-answer-legend` (cursor: view-by)
     * `submission-legend` (cursor:  map-page geo-field, map-page submission-clicked)
       * `single-submission/submission-view`
   * `table-page`
     * `label-changer` (cursor: nil, works on shared cursor `language-cursor`
     * `single-submission/submission-view`
   * `chart-page`
     * `chart-chooser` (cursor: nil)
     * `list-of-charts` (cursor: chart-page)
       * `single-chart` (cursor: chart-page chart-data >element)
   * `settings-page` (cursor: dataset-info)
     * (settings-page will be super basic; it will just display the name / description / active-inactive status, and not allow for any editing of data)

## Hatti structure (proposal)

Hatti will primarily provide a set of Om components that are meant to be used in a dataview such as Zebra. All of Hatti's Om components should be over-rideable, you should be able to over-ride any of the Internal Hatti components easily and have a new dataview that incorporates such over-riding. As such, here is the basic proposal for how Hatti's components should be written:

Components in Hatti should be multi-methods. By default, the dispatch value will be `:view-type` [1] stored in the application shared state.
```clj
(defmulti map-page
  (fn [_ owner & _]
    (om/get-shared owner :view-type)))

(defn map-page :ona-default
  [cursor owner]
  ; actual map-page definition
  ...)
```
This will allow any user to include the `map-page` in their view.
Now say that they want to change the way the map view-by menu is rendered. They would do this by providing a value to correspond to `::view-type` in the `om/root` call, and then overriding whatever component or subcomponent they want implemented differently.
```clj
(derive :my-view :ona-default)
(om/root map-page atom {:target ...
                        :shared {:view-type :my-view}})
(defn view-by-menu :my-view
  [cursor owner]
  ; the view-by-menu definition specific to :my-view
  ...)
```

There may be some components which also need some special treatment *within* other views, eg. a submission-view which is different for the map and the table page. These should be implemented as multi-methods with a different dispatch function that still incorporates the shared `::view-type`, An example could be:
```clj
(defmulti single-submission-view
  (fn [cursor owner & _]
    [(om/get-shared owner :view-type) (-> cursor :selected-view)]))
(defmethod single-submission-view [:ona-default :map]
  (fn [cursor owner opts]
    ...))
(defmethod single-submission-view [:ona-default :table]
  (fn [cursor owner opts]
    ...))
```

[1] - It will actually be the namespace-qualified `::view-type`, but I need to do a bit more research to figure out how to use it properly.

## License

Hatti is released under the [Apache 2.0 License](http://opensource.org/licenses/Apache-2.0).
