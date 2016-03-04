# BringMeTo
BringMeTo is a simple web application that demonstrates the use of the feather-crest API library
to smoothly interface with the CREST API of EVE Online.
The API library is fully asynchronous, and models the CREST using static typing.

## Description
The application consists of a Single-Sign On with an EVE online account,
followed by the option to input an item to search for.
BringMeTo will then attempt to find stations within 10 jumps of the user
that sell that item.
Finally, it presents the user the option to set a waypoint to any of the items.
This is achieved with the use of the following public CREST endpoints:

* itemTypes
* regions
	* market sell orders

Furthermore, the following private CREST endpoints have also been used:

* (decode)
* character
	* location
	* waypoints
* systems

Please have a look at the [website source](src/main/scala/eu/calavoow/bringmeto/BringMeToServlet.scala),
especially the implementation of `/routeItem` where the api library is used most extensively.

## Build & Run
First set up [feather-crest](https://github.com/Calavoow/feather-crest) (refer to that page for more details)
and run

```sh
sbt
publishLocal
```

to set up the library dependency locally.

```sh
$ cd BringMeTo
$ ./sbt
> jetty:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.


## Future Work
The idea of BringMeTo can be extended much further to bring people to:

1. Fights close by, using the zKillboard API.
	Used like this, it may be a valuable FC tool.
1. Ratters close by.
1. Plan a route, taking into account the distance of warps.
	Sometimes, short warps are preferred over more gate jumps, e.g. in a freighter.
1. Plan a route taking into account Thera, or find the closest Thera entrance.
1. Plan a route smartly, avoiding recent gate camps.
1. Closest Faction Warfare space.
1. Closest incursions.
1. Closest trade hubs.
1. Enter waypoints for the EVE circuit, a tour around entire new Eden.
