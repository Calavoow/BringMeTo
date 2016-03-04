package eu.calavoow.bringmeto.api

import dispatch._
import eu.calavoow.bringmeto.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object Login {
	case class AuthCommunicationException(errorCode: Int, msg: String) extends RuntimeException(msg)
	val logger = LoggerFactory.getLogger(getClass)

	/*
	def loginUrl(csrfToken: String) : String = {
		val oathURL = "https://login.eveonline.com/oauth/authorize/"
		val redirectURL = "http://localhost:8080/login"
		val config = Config.readApiConfig
		s"$oathURL?" +
			s"response_type=code" +
			s"&client_id=${config.clientId}" +
			s"&scope=publicData" +
			s"&redirect_uri=$redirectURL" +
			s"&state=$csrfToken"
	}
	*/

	def exchangeAccessCode(accessCode: String)
		(implicit ec: ExecutionContext) : Future[String] = exchangeCode(accessCode, "code", "authorization_code")

	def exchangeRefreshToken(token: String)
		(implicit ec: ExecutionContext) : Future[String] = exchangeCode(token, "refresh_token", "refresh_token")

	def exchangeCode(accessCode: String, codeParam: String, grantType: String)
		(implicit ec: ExecutionContext) : Future[String] = {
		val tokenEndpoint = "https://login.eveonline.com/oauth/token"
		val config = Config.readApiConfig

//		val base64Auth = Base64.encode(s"${config.clientId}:${config.secretKey}".getBytes(StandardCharsets.UTF_8))
		val headers = Map[String,String](
			"Accept" -> "application/json",
			"User-Agent" -> "BringMeTo/0.1"
//			"Authorization" -> s"Basic $base64Auth"
		)
		val body = Map(
			"grant_type" -> grantType,
			codeParam -> accessCode
		)

		// Build the request, adding the body and headers. Finally, add the Basic Authentication.
		val request = url(tokenEndpoint).secure << body <:< headers as_!(config.clientId,config.secretKey)
//		val request = Http(tokenEndpoint)
//			.postForm(Seq("grant_type" → grantType, codeParam → accessCode))
//			.auth(config.clientId, config.secretKey)
//			.timeout(5000, 5000)
		val requestFull = request.toRequest
		logger.trace(s"AccessCode Request, headers : ${requestFull.getHeaders}\n params ${requestFull.getBodyEncoding}")
		val responseFut = Http(request)

		val responseBody = responseFut.map { response =>
			response.getStatusCode match {
				case 200 =>
					logger.trace("Succesfully exchanged auth token")
					response.getResponseBody
				case _ =>
					logger.warn(s"Unsuccessfully exchanged auth token.\n${response.getStatusCode}/${response.getStatusText}\n${response.getResponseBody}")
					throw new AuthCommunicationException(response.getStatusCode,response.getStatusText)
			}
		}
		responseBody.onFailure {
			case ex : Exception =>
				logger.error(s"Exception while requesting new auth token.\n${ex.getMessage}\n${ex.getStackTrace.mkString("","\n","\n")}")
				throw ex
		}

		responseBody

//		try {
//			val result = request.asString
//			if( result.isSuccess ) {
//				Some(result.body)
//			} else {
//				logger.info(s"Exchanging the EVE access code went wrong: $result")
//				None
//			}
//		} catch {
//			case timeout: SocketTimeoutException ⇒
//				logger.warn(s"Timeout while exchanging accessCode $accessCode: $timeout")
//				None
//		}
	}
}
