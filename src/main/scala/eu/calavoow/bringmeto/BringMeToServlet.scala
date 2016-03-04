package eu.calavoow.bringmeto

import java.text.NumberFormat
import javax.swing.text.NumberFormatter

import eu.calavoow.bringmeto.api.Login
import eu.calavoow.bringmeto.config.Config
import eu.calavoow.bringmeto.universe.Universe
import feather.crest.api.CrestLink.CrestCommunicationException
import feather.crest.models._
import spray.json._
import feather.crest.api.CrestLink.CrestProtocol._
import org.scalatra._
import org.slf4j.LoggerFactory
import scala.concurrent.Future
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.async.Async.{async,await}

class BringMeToServlet extends BringmetoStack with ApiFormats with FutureSupport with UrlGeneratorSupport {

	override def executor = scala.concurrent.ExecutionContext.global

	val logger = LoggerFactory.getLogger(getClass)

	val homePage = get("/") {
		val authToken = cookies.get("access_token")

		authToken match {
			case None =>
				val config = Config.readApiConfig
				val loginLink = ""
				<html>
					<head>
						<title>BringMeTo</title>
						<script type="text/javascript" src="js/d3.v3.min.js" charset="utf-8"></script>
						<script type="text/javascript" src="js/cookie.js"></script>
						<script type="text/javascript" src="js/home.js"></script>
						<link rel="stylesheet" href="css/basic.css" />
					</head>
					<body>
						<h1>BringMeTo</h1>
						Where you want to go!<br />
						<strong><a id="login" data-clientid={config.clientId}>Please login</a></strong>
					</body>
				</html>
			case Some(token) =>
				<html>
					<head>
						<title>BringMeTo Homepage</title>
						<script type="text/javascript" src="js/d3.v3.min.js" charset="utf-8"></script>
						<script type="text/javascript" src="js/cookie.js"></script>
						<script type="text/javascript" src="js/home.js"></script>
						<link rel="stylesheet" href="css/basic.css" />
					</head>
					<body>
						<h1>BringMeTo</h1>
						Where I can buy an item! (E.g. "Sacrilege")
						Patience please while we fetch information from numerous sources.
						<form id="itemRouteForm" action="/routeItem">
							<label for="itemName">Item Name (exact):</label>
							<input id="itemName" name="itemName" type="text" />
							<input type="submit" value="Submit" />
						</form>
						<div id="output"></div>

						<a id="logout" href="#">Log Out</a>
					</body>
				</html>
		}
	}

	get("/login") {
		<html>
			<head>
				<script type="text/javascript" src="js/d3.v3.min.js" charset="utf-8"></script>
				<script type="text/javascript" src="js/cookie.js"></script>
				<script type="text/javascript" src="js/login.js"></script>
				<link rel="stylesheet" href="css/basic.css" />
			</head>
			<body>
				Please wait, processing login information.
			</body>
		</html>
	}

	/**
	 * Expects in the message body only the access code or refresh token to be exchanged for the auth token.
	 */
	post("/authCode") {
		val authToken = cookies.get("auth_token")
		if( authToken.isDefined ) halt(400, "User already has a valid authentication token.")

		new AsyncResult { val is =
			request.body match {
				case "" ⇒
					halt(400, "No access code given.")
				case token ⇒
					Login.exchangeAccessCode(token)
			}
		}
	}

	post("/refreshToken") {
		val authToken = cookies.get("access_token")
		if( authToken.isDefined ) halt(400, "User already has a valid authentication token.")

		new AsyncResult { val is =
			request.body match {
				case "" ⇒
					halt(400, "No refresh token given.")
				case token ⇒
					Login.exchangeRefreshToken(token)

			}
		}
	}

	def assumeAuth = {
		val authToken = cookies.get("access_token")
		if(authToken.isEmpty) redirect(url(homePage))
		else authToken
	}

	get("/routeItem") {
		val authToken = assumeAuth
		val itemName : String = params.get("itemName").getOrElse(halt(400, "No item name given"))
		if(itemName.isEmpty) halt(400, "Item name empty")

		val maxJumps = 10

		val out = async {
			// Find the item
			val root = await(Root.public())
			val itemTypes = await(Future.sequence(root.itemTypes.construct(authToken)))
			val item = itemTypes.flatMap(_.items)
				.find(itemType => itemType.name == itemName)
				.getOrElse(halt(400, s"There does not exist an item with name $itemName"))

			// Find the character location
			val authRoot = await(Root.authed())
			val decode = await(authRoot.decode.follow(authToken))
			val character = await(decode.character.follow(authToken))
			val someLocation = await(character.location.follow(authToken))
			val location = someLocation.solarSystem.getOrElse(halt(400, "Character has no location. Are you logged into EVE (in-game)?"))

			// Find nearby regions, that is, within maxJumps.
			val universe = Universe.graph
			val closeSystems = universe.nthOrderNeighbours(universe.getSolarSystem(location.id), maxJumps)
			logger.trace(s"Close systems are: ${closeSystems.mkString("Set(",",",")")}")
			val closeRegions = closeSystems.map(_._2.regionID)

			// And get the item orders from those regions
			val regions = await(root.regions.follow(authToken)).items.filter(reg => closeRegions.contains(reg.id))
			val orders = await(Future.sequence(regions.map(_.follow(authToken))))
			val sellOrders = await(Future.sequence(orders.map(_.marketSellLink(item).follow(authToken))))

			logger.trace(s"Found sell orders: ${sellOrders.mkString(",")}")

			// Now get only the results that have one for sale and are within 10 jumps and return them
			val closeOrders = for(
				relevantRegion <- sellOrders if relevantRegion.totalCount > 0;
				sellOrder <- relevantRegion.items
					if Universe.locationToSolarsystem.contains(sellOrder.location.id) // If its not in a player owned station (TODO later)
						&& closeSystems.map(_._2).contains(Universe.locationToSolarsystem(sellOrder.location.id)) // If the station is in one of the close systems.
			) yield (sellOrder, Universe.locationToSolarsystem(sellOrder.location.id))

			// Sort orders by price, and return their location + price
			logger.trace("Items found:")
			val formatter = NumberFormat.getInstance()
			val sortedOrders = closeOrders.sortBy(_._1.price).map { case(order, solarSystem) =>
				val dist = closeSystems.find(_._2 == solarSystem).get._1

				logger.trace(s"${solarSystem.solarSystemName},$dist,${order.volume},${order.price}")

				// Convert them to html elements
				<tr>
					<td>{solarSystem.solarSystemName}</td>
					<td>{f"${solarSystem.security}%1.1f"}</td>
					<td>{dist}</td>
					<td>{order.volume}</td>
					<td>{formatter.format(order.price)}</td>
					<td><button class="autopilot" direct="true" systemID={s"${solarSystem.solarSystemID}"}>Go</button><button class="autopilot" direct="false" systemID={s"${solarSystem.solarSystemID}"}>Waypoint</button></td>
				</tr>
			}

			<html>
				<head>
					<script type="text/javascript" src="js/d3.v3.min.js" charset="utf-8"></script>
					<script type="text/javascript" src="js/cookie.js"></script>
					<script type="text/javascript" src="js/route.js"></script>
					<link rel="stylesheet" href="css/basic.css" />
				</head>
				<body>
					<h1>BringMeTo</h1>
					Nearby Sellers of {itemName} (within {maxJumps} jumps)
					<div id="notifications" style="height:2em"></div>
					<table id="outputTable">
						<tr><th>System name</th><th>Security</th><th>Jumps</th><th>Volume</th><th>Price</th><th>Set Autopilot</th></tr>
						{sortedOrders}
					</table>
					<a id="logout" href="#">Log Out</a>
				</body>
			</html>
		}
		new AsyncResult() { val is = out }
	}


	get("/autopilot") {
		val authToken = assumeAuth
		val systemID = params.get("systemID").getOrElse(halt(400, "No system ID given")).toInt
		val direct = params.get("direct").map(_.toBoolean).getOrElse(false)

		val universe = Universe.graph
		val destination = universe.getSolarSystem.getOrElse(systemID, halt(400, "Given solar system ID is not valid."))

		val waypointRequest = async {
			val root = await(Root.authed())
			val decode = await(root.decode.follow(authToken))
			val character = await(decode.character.follow(authToken))

			// Find the crestlink to the solarsystem
			val solarSystems = await(root.systems.follow(authToken)).items
			val systemLink = solarSystems.find(_.id == destination.solarSystemID).get
			val idLink = IdCrestLink[SolarSystem](id = systemLink.id, id_str = systemLink.id_str, href = systemLink.href)

			// Construct the waypoint to send to the CREST endpoint
			val waypoint = Waypoints(clearOtherWaypoints = direct, first = false, solarSystem = idLink)
			character.waypoints.post(waypoint, authToken)
		}

		new AsyncResult() { val is = waypointRequest }
	}

}
