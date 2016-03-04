package eu.calavoow.bringmeto.universe

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.io.Source

case class SolarSystem(
	regionID: Int,
	solarSystemID: Int,
	solarSystemName: String,
	security: Double
) {
	override def equals(other: Any) = {
		other match {
			case that : SolarSystem =>
				solarSystemID == that.solarSystemID
			case _ => false
		}
	}
	override def hashCode : Int = solarSystemID
}

object Graph {
	def fromFile = {
		val solarSystemLines = Source.fromURL(getClass.getResource("/mapSolarSystems")).getLines()
		val solarSystems = solarSystemLines.map(_.split(",")).map { array =>
			SolarSystem(array(0).toInt, array(1).toInt, array(2), array(3).toDouble)
		}

		val jumpLines = Source.fromURL(getClass.getResource("/mapSolarSystemJumps")).getLines()
		val edgesById = jumpLines.map(_.split(",")).map { array =>
			(array(0).toInt, array(1).toInt)
		}

		// First set up the adjacency list, later fill the edges.
		val mutableAdjacency = solarSystems.map( (_, ListBuffer.empty[SolarSystem])).toMap
		val idToSolarSystem = solarSystems.map(s => s.solarSystemID -> s).toMap
		for((from, to) <- edgesById) {
			val fromSolarSystem = idToSolarSystem(from)
			val toSolarSystem = idToSolarSystem(to)
			mutableAdjacency(fromSolarSystem) += toSolarSystem
			mutableAdjacency(toSolarSystem) += fromSolarSystem
		}

		val adjacencyList = mutableAdjacency.map { case (key,value) => (key, value.toList)}
		Graph(adjacencyList)
	}
}

case class Graph(adjacencyList : Map[SolarSystem, Seq[SolarSystem]]) {
	def neighbours(s : SolarSystem) = adjacencyList(s)

	def nthOrderNeighbours(s: SolarSystem, order: Int) = {
		@tailrec
		def recursiveNeighbourhood(border: Set[SolarSystem], order: Int, neighbourhood : Set[SolarSystem]) : Set[SolarSystem] = {
			if(order == 0) {
				neighbourhood
			} else {
				// expand the neighbourhood
				val adjacentNodes = for(node <- border) yield {
					adjacencyList(node)
				}
				val adj = adjacentNodes.flatten
				val newNodes = adj.diff(neighbourhood)

				recursiveNeighbourhood(newNodes, order - 1, neighbourhood ++ newNodes)
			}
		}

		recursiveNeighbourhood(Set(s), order, Set(s))
	}
}
