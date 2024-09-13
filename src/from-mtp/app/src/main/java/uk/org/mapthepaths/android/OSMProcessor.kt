package uk.org.mapthepaths.android


import de.westnordost.osmapi.OsmConnection
import de.westnordost.osmapi.map.MapDataDao
import de.westnordost.osmapi.map.data.*
import de.westnordost.osmapi.map.handler.MapDataHandler
import de.westnordost.osmapi.notes.NotesDao
import freemap.data.Point
import kotlinx.coroutines.*
import oauth.signpost.OAuthConsumer
import oauth.signpost.basic.DefaultOAuthConsumer


import java.util.*


class OSMProcessor(private val apiUrl: String, private val userAgent: String, private val scope: CoroutineScope, private var displayCallback: ((Long, MutableMap<String, String>, DoubleArray) -> Unit)?) {


    data class WayMatch(val wayId: Long, val dist: Double, val segIdx: Int)

    private var nodes: MutableMap<Long, Node> = HashMap()
    private var ways: MutableMap<Long, Way> = HashMap()
    private val completedFullNodeWays: HashMap<Long, FullNodeWay> = HashMap()

    


    private val handler = object : MapDataHandler {
        override fun handle(way: Way) {
            if (way.tags != null && way.tags["highway"] != null) {
                ways[way.id] = way
            }
        }

        override fun handle(node: Node) {
            nodes[node.id] = node
        }

        override fun handle(relation: Relation) {
        }

        override fun handle(bounds: BoundingBox) {
        }
    }


    fun download(llBounds: DoubleArray,
                 downloadComplete: () -> Unit, onError: (String) -> Unit) {

        val downloader = OsmConnection(apiUrl, userAgent)
        val mapDataDao = MapDataDao(downloader)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    mapDataDao.getMap(BoundingBox(llBounds[0], llBounds[1], llBounds[2], llBounds[3]), handler)

                    ways.mapValuesTo(completedFullNodeWays) { FullNodeWay(it.value, nodes) }

                }


                completedFullNodeWays.forEach {


                    val points = DoubleArray(it.value.nodes.size * 2)
                    var i = 0

                    it.value.nodes.forEach { n ->
                        points[i++] = n.position.latitude
                        points[i++] = n.position.longitude
                    }
                    displayCallback?.invoke(it.key, it.value.way.tags, points)
                }
                if (scope.coroutineContext.isActive) {
                    downloadComplete()
                }

            } catch (e: Exception) {
                if (scope.coroutineContext.isActive) {
                    onError("with bbox ${llBounds[1]},${llBounds[0]},${llBounds[3]} ${llBounds[2]}: error $e")
                }
            }
        }

    }

    // find the nearest way(s) to a Point
    // returns a sorted-by-distance mutable list of matches
    // each match contains a way ID, a distance to the way (metres) and a nearest node, if there is one
    // 25.0 is too large a distance for inserting nodes of surveyed path into other ways !!!

    fun findNearestWay(lonLat: Point, thresholdDistance: Double = 25.0, wayId: Long = 0L): MutableList<WayMatch> {
        var curPoint: Point
        var lastPoint: Point
        val matches = mutableListOf<WayMatch>()
        var dist: Double
        var matchingWay: Long
        var nearestWayDist = Double.MAX_VALUE
        var segIdx = 0

        // Only consider ways uploaded to OSM, not newly created not-uploaded-yet ways.
        // 290319 consider newly created ways with ID closer to 0 than the way under test if wayId is negative (e.g. allows insertion of terminals of way -2 into way -1)
        ways.filter { it.key > wayId }.forEach {
            matchingWay = 0L

            for (i in 0 until it.value.nodeIds.size) {
                val node = nodes[it.value.nodeIds[i]]
                if (node != null) {
                    curPoint = Point(node.position.longitude, node.position.latitude)

                    // If we haven't found a segment near to the point yet, search for one.
                    // 220818 this will not necessarily find the NEAREST segment - but the first one below the threshold; so remove the matchingWay check
                    if (i >= 1) {
                        val lastNode = nodes[it.value.nodeIds[i - 1]]
                        if (lastNode != null) {
                            lastPoint = Point(lastNode.position.longitude, lastNode.position.latitude)

                            dist = lonLat.haversineDistToLine(lastPoint, curPoint)

                            if (dist >= 0 && dist < thresholdDistance && dist < nearestWayDist) {
                                matchingWay = it.key
                                nearestWayDist = dist
                                segIdx = i - 1 // segment index is index of lastPoint (first point of the segment)
                            }
                        }
                    }
                }
            }

            // If this way was within range then add it and its distance, as well as the intersecting segment
            if (matchingWay != 0L) {
                matches.add(WayMatch(matchingWay, nearestWayDist, segIdx))
            }
        }

        matches.sortBy { it.dist } // 220818 corrected this
        return matches
    }


    fun getTag(wayId: Long, tag: String): String {
        return ways[wayId]?.tags?.get(tag) ?: "None"
    }

    fun setTags(wayId: Long, tags: HashMap<String, String>, consumer: OAuthConsumer, onSuccess: (Long) -> Unit, onError: (String) -> Unit) {

        if (ways[wayId] != null) {
            val w: Way = ways[wayId]!! // we know ways[wayId] will not be null

            tags.forEach {
                w.tags[it.key] = it.value
                if (it.value != "") {
                    w.tags[it.key] = it.value
                } else {
                    w.tags.remove(it.key)
                }
            }

            scope.launch {

                try {
                    var changeset = 0L
                    withContext(Dispatchers.IO) {
                        val localConsumer = DefaultOAuthConsumer(consumer.consumerKey, consumer.consumerSecret)
                        localConsumer.setTokenWithSecret(consumer.token, consumer.tokenSecret)
                        val uploadConn = OsmConnection(apiUrl, userAgent, localConsumer)
                        val elemList = mutableListOf<Element>(w)
                        changeset = MapDataDao(uploadConn).updateMap(hashMapOf("comment" to "Update of tags on way with ID $wayId", "created_by" to "MapThePaths Android app"), elemList)
                        {
                            val oldWay = ways[it.serverId]
                            if (oldWay != null) {
                                ways[it.serverId] = OsmWay(it.serverId, it.serverVersion, oldWay.nodeIds, oldWay.tags)
                            }
                        }
                    }

                    if (scope.coroutineContext.isActive) {
                        onSuccess(changeset)
                    }

                } catch (e: Exception) {
                    if (scope.coroutineContext.isActive) {
                        onError("$e. (If using MapThePaths 0.2.3 or higher for the first time, this can be resolved by logging out of OSM and logging in again. The OSM connection info has changed.)")
                    }
                }
            }
        }
    }

    fun uploadNote(note: String, position: LatLon, consumer: OAuthConsumer?,
                   onError: (String) -> Unit, onSuccess: (Long) -> Unit) {
        scope.launch {
            try {
                var noteId = 0L
                withContext(Dispatchers.IO) {
                    var localConsumer: OAuthConsumer? = null
                    if (consumer != null) {
                        localConsumer = DefaultOAuthConsumer(consumer.consumerKey, consumer.consumerSecret)
                        localConsumer.setTokenWithSecret(consumer.token, consumer.tokenSecret)
                    }
                    val uploadConn = OsmConnection(apiUrl, userAgent, localConsumer)
                    noteId = NotesDao(uploadConn).create(position, note).id
                }
                if (scope.coroutineContext.isActive) {
                    onSuccess(noteId)
                }
            } catch (e: Exception) {
                if (scope.coroutineContext.isActive) {
                    onError("$e. (If using MapThePaths 0.2.3 or higher for the first time, this can be resolved by logging out of OSM and logging in again. The OSM connection info has changed.)")
                }
            }
        }

    }

    fun onDestroy() {
        displayCallback = null
    }
}
