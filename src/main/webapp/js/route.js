"use strict";

window.onload = function() {
	var access_token = docCookies.getItem("access_token");
	var refresh_token = docCookies.getItem("refresh_token");

	if(access_token){
		d3.selectAll(".autopilot").on("click", function() {
			// add loading notification
			d3.select("#notifications").style({"background-color":"#D0F0C0"}).html(
				"Loading, please wait..."
			)

			var el = d3.select(this)
			var systemID = el.attr("systemID")
			var direct = el.attr("direct")

			var xhrRequest = d3.xhr("autopilot?systemID=" + systemID + "&direct=" + direct, function(error, data) {
				if(error){
					d3.select("#notifications").style({"bacgkround-color":"#ED2939"})
					alert(error.response)
				} else {
					d3.select("#notifications").style({"background-color": "#DDD"}).html("Successfully set waypoint.")
				}
			})

		})
	} else {
		if(window.confirm("You are not authenticated, please log in on the homepage.")){
			window.location.replace("/");
		}
	}

	d3.select("#logout").on("click", function() {
		docCookies.removeItem("access_token");
		docCookies.removeItem("refresh_token");
		docCookies.removeItem("token_type")
		window.location.reload();
	})
}