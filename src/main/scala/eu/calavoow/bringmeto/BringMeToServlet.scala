package eu.calavoow.bringmeto

import java.text.NumberFormat
import javax.swing.text.NumberFormatter

import eu.calavoow.bringmeto.api.Login
import eu.calavoow.bringmeto.config.Config
import eu.calavoow.bringmeto.universe.Universe
import feather.crest.api.CrestLink.CrestCommunicationException
import feather.crest.models.{Character, Root}
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
						<title>BringMeTo Homepage</title>
						<script type="text/javascript" src="js/d3.v3.min.js" charset="utf-8"></script>
						<script type="text/javascript" src="js/cookie.js"></script>
						<script type="text/javascript" src="js/home.js"></script>
					</head>
					<body>
						<h1 style="display:none">
							<a id="login" data-clientid={config.clientId}>Please login</a>
						</h1>
					</body>
				</html>
			case Some(token) =>
				<html>
					<head>
						<title>BringMeTo Homepage</title>
						<script type="text/javascript" src="js/d3.v3.min.js" charset="utf-8"></script>
						<script type="text/javascript" src="js/cookie.js"></script>
						<script type="text/javascript" src="js/home.js"></script>
					</head>
					<body>
						<form id="itemRouteForm" action="/routeItem">
							<label for="itemName">Item Name (exact):</label>
							<input id="itemName" name="itemName" type="text" />
							<input type="submit" value="Submit" />
						</form>
						<div id="output"></div>
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
		contentType = formats("json")

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
		contentType = formats("json")

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
		if(authToken.isEmpty) halt(400, "User has no access token. Wait a second...")
		else authToken
	}

	get("/routeItem") {
		contentType = "json"

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
			logger.debug(s"Close systems are: ${closeSystems.mkString("Set(",",",")")}")
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
					<td><button name="goHere" systemID={s"${solarSystem.solarSystemID}"}>Go</button><button name="waypoint" systemID={s"${solarSystem.solarSystemID}"}>Waypoint</button></td>
				</tr>
			}

			<html>
				<head>
					<script type="text/javascript" src="js/d3.v3.min.js" charset="utf-8"></script>
					<script type="text/javascript" src="js/cookie.js"></script>
					<script type="text/javascript" src="js/route.js"></script>
				</head>
				<body>
					<h1>Nearby Sellers of {itemName} (within {maxJumps} jumps)</h1>
					<table>
						<tr><td>System name</td><td>Security</td><td>Jumps</td><td>Volume</td><td>Price</td><td>Set Autopilot</td></tr>
						{sortedOrders}
					</table>
				</body>
			</html>
		}
		new AsyncResult() { val is = out }
	}


	post("/autopilot") {
		val systemID = params.get("systemID").getOrElse(halt(400, "No system ID given"))
		val direct = params.get("direct").getOrElse(false)
	}

}
