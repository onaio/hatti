var system = require("system"); // jshint ignore:line
var page = require("webpage").create();

var url = system.args[1];

page.onConsoleMessage = function (message) {
    console.log(message);
};

function exit(code) {
    setTimeout(function(){ phantom.exit(code); }, 0);
    phantom.onError = function(){};
}

console.log("Loading URL: " + url);

page.open(url, function (status) {
    if (status != "success") {
        console.log("Failed to open " + url);
        phantom.exit(1);
    }

    console.log("Running test.");

    var failures = page.evaluate(function() {
        test.test_runner.runner(); // jshint ignore:line
        return window["test-failures"];
    });

    if (failures === 0) {
        console.log("Tests succeeded.");
    }
    else {
        console.log("*** Tests failed! ***");
    }
    phantom.exit(failures || (failures !== 0 && !failures) ? 100 : 0);
});
