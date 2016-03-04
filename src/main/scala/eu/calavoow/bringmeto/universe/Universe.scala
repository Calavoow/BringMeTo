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

object Universe {
	private def fromFile : Universe = {
		val solarSystemLines = Source.fromURL(getClass.getResource("/mapSolarSystems.csv")).getLines()
		val solarSystems = solarSystemLines.drop(1).map(_.split(",")).map { array =>
			SolarSystem(array(0).toInt, array(1).toInt, array(2), array(3).toDouble)
		}.toSeq

		val jumpLines = Source.fromURL(getClass.getResource("/mapSolarSystemJumps.csv")).getLines()
		val edgesById = jumpLines.drop(1).map(_.split(",")).map { array =>
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
		Universe(adjacencyList)
	}

	private def locationsFromFile(universe : Universe) : Map[Int,SolarSystem] = {
		val locationLines = Source.fromURL(getClass.getResource("/locationToSolarSystem.csv")).getLines()
		val locationsToSolarSystems = locationLines.drop(1).map(_.split(",")).map { array =>
			val locationID = array(0).toInt
			val solarSystemID = array(1).toInt
			locationID -> universe.getSolarSystem(solarSystemID)
		}.toMap

		locationsToSolarSystems
	}

	val graph = fromFile
	val locationToSolarsystem = locationsFromFile(graph)
}

case class Universe(adjacencyList : Map[SolarSystem, Seq[SolarSystem]]) {
	val getSolarSystem = adjacencyList.keys.map(s => s.solarSystemID -> s).toMap

	def neighbours(s : SolarSystem) = adjacencyList(s)

	/**
	 * Returns the nth order neighbourhood of a node and its distance from the startnode.
	 *
	 * The nth order neighbourhood is breadth first search-like where we stop at the given order as distance.
	 */
	def nthOrderNeighbours(s: SolarSystem, order: Int) : Set[(Int,SolarSystem)] = {
		@tailrec
		def recursiveNeighbourhood(border: Set[SolarSystem], steps: Int, neighbourhood : Set[(Int,SolarSystem)]) : Set[(Int,SolarSystem)] = {
			if(steps == 0) {
				neighbourhood
			} else {
				// expand the neighbourhood
				val adjacentNodes = for(node <- border) yield {
					neighbours(node)
				}
				val adj = adjacentNodes.flatten
				val newNodes = adj.diff(neighbourhood.map(_._2))

				recursiveNeighbourhood(newNodes, steps - 1, neighbourhood ++ newNodes.map((order - steps,_)))
			}
		}

		recursiveNeighbourhood(Set(s), order, Set((0,s)))
	}
}
