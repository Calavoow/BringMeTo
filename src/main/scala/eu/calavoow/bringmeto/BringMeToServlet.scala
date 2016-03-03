package eu.calavoow.bringmeto

import org.scalatra._
import org.slf4j.LoggerFactory

class BringMeToServlet extends BringmetoStack {
	val logger = LoggerFactory.getLogger(getClass)

	get("/") {
		logger.debug("Hello world")
		<html>
			<body>
				<h1>Hello, world!</h1>
				Say
				<a href="hello-scalate">hello to Scalate</a>.
			</body>
		</html>
	}

}
