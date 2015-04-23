"use strict";
/**
 * Leaflet map.
 *
 * @see http://leaflet.cloudmade.com/
 * @namespace
 * @externs
 */
var L = {};

/** * @constructor */
L.Map = function(id, options){};
/** * @return {!L.Map} */
L.map = function(id, options){};

L.Map.prototype.addLayer = function(layer){};
L.Map.prototype.removeLayer = function(layer){};
L.Map.prototype.setView = function(a, b){};
L.Map.prototype.getBounds = function(a, b){};
L.Map.prototype.fitBounds = function(a, b){};
L.Map.prototype.invalidateSize = function(a, b){};

/** @constructor */
L.LatLng = function(a, b) {};
/** @return {!L.LatLng} */
L.latLng = function(a, b) {};

/** @constructor */
L.Bounds = function(a, b) {};
/** @return {!L.LatLng} */
L.bounds = function(a, b) {};
L.Bounds.prototype.contains = function(b){};

/** * @constructor **/
L.Marker = function(latLng, options){};
L.Marker.prototype.bindPopup = function(a){};
L.Marker.prototype.openPopup = function(){};
L.Marker.prototype.setStyle = function(o){};
L.Marker.prototype.bringToFront = function(){};
L.Marker.prototype.on = function(e, f){};
/** * @return {!L.Marker} */
L.marker = function(latLng, options) {};
/** * @return {!L.Marker} */
L.circleMarker = function(latLng, options) {};


/** @constructor */
L.TileLayer = function(a, b) {};
/** * @return {!L.TileLayer} */
L.tileLayer = function(a,b) {};

/** @constructor */
L.Icon = function(a){};
L.Icon.prototype.iconSize = null;
L.Icon.prototype.shadowSize = null;
L.Icon.prototype.iconAnchor = null;
L.Icon.prototype.popupAnchor = null;

/** @constructor */
L.Point = function(a, b){};

/** @constructor */
L.Layer = function () {};

/** @constructor */
L.GeoJSON = function(a, b) {}
L.GeoJSON.prototype.addTo = function(a, b) {}
/** @return {L.FeatureLayer} */
L.GeoJSON.prototype.getLayers = function() {}
/** * @return {!L.GeoJSON} */
L.geoJson = function(a,b) {}

/** @constructor */
L.FeatureLayer = function(o) {}
L.FeatureLayer.prototype.getBounds = function() {};

/** @namespace */
L.control = {};
/** @constructor */
L.control.Layers = function(a, o) {};
/** @return {L.control.Layers} */
L.control.layers = function(a, o) {};
/** @constructor */
L.control.Zoom = function(a, o) {};
/** @return {L.control.Zoom} */
L.control.zoom = function(a, o) {};

/** @constructor */
L.Google = function(a, b) {};
