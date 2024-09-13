package uk.org.mapthepaths.android

import de.westnordost.osmapi.map.data.Way
import de.westnordost.osmapi.map.data.Node


// FullNodeWay
// a way containing references to its actual Node objects, and also with a bounding box

class FullNodeWay (val way: Way, n:MutableMap<Long, Node>) {
    private var bbox = BBox()
    var nodes: List<Node> = Array(way.nodeIds.size) { n[way.nodeIds[it]] }.mapNotNull { it }

    init {
        nodes.forEach { bbox.extend(it.position.longitude, it.position.latitude)}
    }

}