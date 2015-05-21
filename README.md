# hatti-examples

A branch for showcasing examples built with [hatti](http://github.com/onaio/hatti). This branch (gh-pages) is separate from the `master` branch; it could also have been its own repository.

At present, we have the following examples:
 * [osm](http://onaio.github.io/hatti/examples/osm/)
 * [stolen](http://onaio.github.io/hatti/examples/stolen/)

## Development
If you are working on this branch as a testing mechanism, but developing hatti/master, I would recomment the following setup:
 * A directory called `hatti` which is linked to `master` or feature branches off of master.
 * A directory called `hatti-web` or `hatti-examples` which contains this `gh-pages` branch or feature branches off of `gh-pages`
 * A directory inside `hatti-web` called `checkouts`. Inside `checkouts`, symlink over to `hatti` so that your live changes will be picked up in the hatti examples.

## License

Hatti Examples is released under the [Apache 2.0 License](http://opensource.org/licenses/Apache-2.0).
