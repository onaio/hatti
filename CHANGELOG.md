v0.1.6
 * remove extra metadata fields in table view for filtered dataviews
 * tests
 * don't use count for num_of_submissions if zero
 * map zoom fix for datasets that contain -ve, +ve lat,lng values.

v0.1.4
 * filter added for categorical view bys
 * cljx converted to cljc

v0.1.3:
 * solves [#15](https://github.com/onaio/hatti/issues/15) - image names don't vanish if URLs aren't found.
 * :map view is the default
 * no error if `:views :all` has unexpected values
