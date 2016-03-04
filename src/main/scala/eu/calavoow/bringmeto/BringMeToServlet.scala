package eu.calavoow.bringmeto

import eu.calavoow.bringmeto.api.Login
import eu.calavoow.bringmeto.config.Config
import feather.crest.models.{Character, Root}
import feather.crest.api.CrestLink.CrestProtocol._
import org.scalatra._
import org.slf4j.LoggerFactory
import spray.json.JsonWriter
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

class BringMeToServlet extends BringmetoStack with ApiFormats with FutureSupport with UrlGeneratorSupport {

	override def executor = scala.concurrent.ExecutionContext.global

	val logger = LoggerFactory.getLogger(getClass)

	get("/") {
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
						<form action="/routeItem">
							<label for="itemID">Item ID:</label>
							<input id="itemID" name="itemID" type="text" />
							<input type="submit" value="Submit" />
						</form>
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
		if(authToken.isEmpty) halt(401, "The authentication token is not set.")
		else authToken
	}

	get("/routeItem") {
		val authToken = assumeAuth
		val itemID = params.get("itemID").getOrElse(halt(400, "No item ID set"))

		// Find the character location
		val charLoc = for(
			r <- Root.fetch();
			decode <- r.decode.follow(authToken);
			character <- decode.character.follow(authToken);
			location <- character.location.follow(authToken)
		) yield {
			location.solarSystem match {
				case None =>
					halt(400, "Character has no location. Are you logged into EVE?")
				case Some(loc) =>
					(character, loc)
			}
		}
	}

}
