

import org.apache.spark._

import org.apache.spark.rdd.RDD
import org.apache.spark.util.IntParam
// import classes required for using GraphX
import org.apache.spark.graphx._
import org.apache.spark.graphx.util.GraphGenerators

case class Flight(dofM:String, dofW:String, carrier:String, tailnum:String, flnum:Int, org_id:Long, origin:String, dest_id:Long, dest:String, crsdeptime:Double, deptime:Double, depdelaymins:Double, crsarrtime:Double, arrtime:Double, arrdelay:Double,crselapsedtime:Double,dist:Int)
 
def parseFlight(str: String): Flight = {
  val line = str.split(",")
  Flight(line(0), line(1), line(2), line(3), line(4).toInt, line(5).toLong, line(6), line(7).toLong, line(8), line(9).toDouble, line(10).toDouble, line(11).toDouble, line(12).toDouble, line(13).toDouble, line(14).toDouble, line(15).toDouble, line(16).toInt)
}

//Create RDD with the January 2014 data 
val textRDD = sc.textFile("/user/user01/data/rita2014jan.csv")

val flightsRDD = textRDD.map(parseFlight).cache()

val airports = flightsRDD.map(flight => (flight.org_id, flight.origin)).distinct    
    airports.take(1)
//  Array((14057,PDX))

// Defining a default vertex called nowhere
val nowhere = "nowhere"

val routes = flightsRDD.map(flight => ((flight.org_id, flight.dest_id), flight.dist)).distinct

// Array(((14869,14683),1087), ((14683,14771),1482)) 
routes.cache
routes.take(1)
//res79: Array[((Long, Long), Int)] = Array(((10299,10926),160))

// AirportID is numerical - Mapping airport ID to the 3-letter code
val airportMap = airports.map { case ((org_id), name) => (org_id -> name) }.collect.toList.toMap

//airportMap: scala.collection.immutable.Map[Long,String] = Map(13024 -> LMT, 10785 -> BTV, 14574 -> ROA, 14057 -> PDX, 13933 -> ORH, 11898 -> GFK, 14709 -> SCC, 15380 -> TVC,
    
// Defining the routes as edges
val edges = routes.map { case ((org_id, dest_id), distance) => Edge(org_id.toLong, dest_id.toLong, distance) }

edges.take(1)
//res80: Array[org.apache.spark.graphx.Edge[Int]] = Array(Edge(10299,10926,160))

//Defining the Graph
val graph = Graph(airports, edges, nowhere)

// LNumber of airports   
val numairports = graph.numVertices
// numairports: Long = 301
graph.vertices.take(2)

graph.edges.take(2)
// res6: Array[org.apache.spark.graphx.Edge[Int]] = Array(Edge(10135,10397,692), Edge(10135,13930,654))

// which routes >  1000 miles distance?
 graph.edges.filter { case ( Edge(org_id, dest_id,distance))=> distance > 1000}.take(3)
// res9: Array[org.apache.spark.graphx.Edge[Int]] = Array(Edge(10140,10397,1269), Edge(10140,10821,1670), Edge(10140,12264,1628))

// Number of routes
val numroutes = graph.numEdges
// numroutes: Long = 4090

// The EdgeTriplet class extends the Edge class by adding the srcAttr and dstAttr members which contain the source and destination properties respectively.   
graph.triplets.take(3).foreach(println)
((10135,ABE),(10397,ATL),692)
((10135,ABE),(13930,ORD),654)
((10140,ABQ),(10397,ATL),1269)

// Define a reduce operation to compute the highest degree vertex
def max(a: (VertexId, Int), b: (VertexId, Int)): (VertexId, Int) = {
  if (a._2 > b._2) a else b
}

// Compute the max degrees
val maxInDegree: (VertexId, Int)  = graph.inDegrees.reduce(max)
// maxInDegree: (org.apache.spark.graphx.VertexId, Int) = (10397,152)
val maxOutDegree: (VertexId, Int) = graph.outDegrees.reduce(max)
// maxOutDegree: (org.apache.spark.graphx.VertexId, Int) = (10397,153)
val maxDegrees: (VertexId, Int)   = graph.degrees.reduce(max)
// maxDegrees: (org.apache.spark.graphx.VertexId, Int) = (10397,305)
airportMap(10397)
// res70: String = ATL
// we can compute the in-degree of each vertex (defined in GraphOps) by the following:
// which airport has the most incoming flights?
graph.inDegrees.collect.sortWith(_._2 > _._2).map(x => (airportMap(x._1), x._2))
//res46: Array[(String, Int)] = Array((ATL,152), (ORD,145), (DFW,143), (DEN,132), (IAH,107), (MSP,96), (LAX,82), (EWR,82), (DTW,81), (SLC,80), 
val maxIncoming = graph.inDegrees.collect.sortWith(_._2 > _._2).map(x => (airportMap(x._1), x._2)).take(3)
maxIncoming.foreach(println)
(ATL,152)
(ORD,145)
(DFW,143)

// which airport has the most outgoing flights?
graph.outDegrees.join(airports).sortBy(_._2._1, ascending=false).take(1)
val maxout= graph.outDegrees.join(airports).sortBy(_._2._1, ascending=false).take(3)

maxout.foreach(println)
(10397,(153,ATL))
(13930,(146,ORD))
(11298,(143,DFW))

val maxOutgoing = graph.outDegrees.collect.sortWith(_._2 > _._2).map(x => (airportMap(x._1), x._2)).take(3)
maxOutgoing.foreach(println)
(ATL,152)
(ORD,145)
(DFW,143)

// What are the top 10 flights from airport to airport?
graph.triplets.sortBy(_.attr, ascending=false).map(triplet =>
        "There were " + triplet.attr.toString + " flights from " + triplet.srcAttr + " to " + triplet.dstAttr + ".").take(10)
res15: Array[String] = Array(There were 4983 flights from JFK to HNL., There were 4983 flights from HNL to JFK., There were 4963 flights from EWR to HNL., There were 4963 flights from HNL to EWR., There were 4817 flights from HNL to IAD., There were 4817 flights from IAD to HNL., There were 4502 flights from ATL to HNL., There were 4502 flights from HNL to ATL., There were 4243 flights from HNL to ORD., There were 4243 flights from ORD to HNL.)


val sourceId: VertexId = 13024
// 50 + distance / 20 
 graph.edges.filter { case ( Edge(org_id, dest_id,distance))=> distance > 1000}.take(3)
 val gg = graph.mapEdges(e=> e.distance > 1000)

val gg = graph.mapEdges(e => 50.toDouble + e.attr.toDouble/20  )
val initialGraph = gg.mapVertices((id, _) => if (id == sourceId) 0.0 else Double.PositiveInfinity)

val sssp = initialGraph.pregel(Double.PositiveInfinity)(
  (id, dist, newDist) => math.min(dist, newDist), // Vertex Program
  triplet => {  // Send Message
    if (triplet.srcAttr + triplet.attr < triplet.dstAttr) {
      Iterator((triplet.dstId, triplet.srcAttr + triplet.attr))
    } else {
      Iterator.empty
    }
  },
  (a,b) => math.min(a,b) // Merge Message
)
println(sssp.vertices.take(4).mkString("\n"))
(10208,277.79999999999995)
(10268,260.7)
(14828,261.65)
(14698,125.25)

scala> println(sssp.edges.take(4).mkString("\n"))
Edge(10135,10397,84.6)
Edge(10135,13930,82.7)
Edge(10140,10397,113.45)
Edge(10140,10821,133.5)

sssp.vertices.collect.map(x => (airportMap(x._1), x._2)).sortWith(_._2 < _._2)
res21: Array[(String, Double)] = Array((LMT,0.0), (PDX,62.05), (SFO,65.75), (EUG,117.35)



			        



