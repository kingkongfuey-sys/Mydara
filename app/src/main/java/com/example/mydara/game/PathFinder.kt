package com.example.mydara.game

/** Plain BFS over the link graph. */
object PathFinder {

    /**
     * Shortest path (fewest hops) from [from] to [to].
     * The result excludes [from] and includes [to]; so `path[0]` is the first hop.
     * Returns an empty list if from === to, or null if [to] is unreachable.
     * [passable] only restricts intermediate planets; [from] and [to] are always allowed.
     */
    fun findPath(from: Planet, to: Planet, passable: (Planet) -> Boolean = { true }): List<Planet>? {
        if (from === to) return emptyList()
        val prev = HashMap<Planet, Planet>()
        val queue = ArrayDeque<Planet>()
        queue.addLast(from)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (link in cur.links) {
                val next = link.other(cur)
                if (next === from || prev.containsKey(next)) continue
                if (next !== to && !passable(next)) continue
                prev[next] = cur
                if (next === to) {
                    val path = ArrayList<Planet>()
                    var node: Planet = to
                    while (node !== from) {
                        path += node
                        node = prev.getValue(node)
                    }
                    path.reverse()
                    return path
                }
                queue.addLast(next)
            }
        }
        return null
    }

    fun isConnected(planets: List<Planet>): Boolean {
        if (planets.isEmpty()) return true
        val seen = HashSet<Planet>()
        val queue = ArrayDeque<Planet>()
        seen += planets[0]
        queue.addLast(planets[0])
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (link in cur.links) {
                val n = link.other(cur)
                if (seen.add(n)) queue.addLast(n)
            }
        }
        return seen.size == planets.size
    }
}
