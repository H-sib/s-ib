package com.hsib.sib.core.plan

/**
 * Dinic's maximum flow on integer capacities.
 *
 * Written iteratively on purpose: augmenting paths through reverse edges can be as deep as the node
 * count, and the planner must never crash a phone with a stack overflow on a large backlog.
 * Supports raising an edge capacity after a run, so flow can be added incrementally in priority order.
 */
class MaxFlow(private val n: Int) {
    private val head = IntArray(n) { -1 }
    private var to = IntArray(64)
    private var next = IntArray(64)
    private var cap = IntArray(64)
    private var edgeCount = 0
    private val level = IntArray(n)
    private val cursor = IntArray(n)
    private val queue = IntArray(n)
    private val pathEdge = IntArray(n + 1)

    /** Adds a forward edge and its zero capacity reverse edge. Returns the forward edge id. */
    fun addEdge(u: Int, v: Int, capacity: Int): Int {
        require(capacity >= 0) { "negative capacity" }
        ensure(edgeCount + 2)
        val e = edgeCount
        to[e] = v; cap[e] = capacity; next[e] = head[u]; head[u] = e
        to[e + 1] = u; cap[e + 1] = 0; next[e + 1] = head[v]; head[v] = e + 1
        edgeCount += 2
        return e
    }

    private fun ensure(size: Int) {
        if (size <= to.size) return
        val newSize = maxOf(size, to.size * 2)
        to = to.copyOf(newSize); next = next.copyOf(newSize); cap = cap.copyOf(newSize)
    }

    /** Flow currently routed over forward edge [e]. */
    fun flowOn(e: Int): Int = cap[e xor 1]

    /** Raises the capacity of forward edge [e] to [newCapacity] (must not drop below the current flow). */
    fun setCapacity(e: Int, newCapacity: Int) {
        val f = flowOn(e)
        require(newCapacity >= f) { "cannot lower capacity below current flow" }
        cap[e] = newCapacity - f
    }

    /** Pushes as much additional flow as possible from [s] to [t]. Returns the extra flow. */
    fun run(s: Int, t: Int): Int {
        var total = 0
        while (bfs(s, t)) {
            for (i in 0 until n) cursor[i] = head[i]
            total += blocking(s, t)
        }
        return total
    }

    private fun bfs(s: Int, t: Int): Boolean {
        java.util.Arrays.fill(level, -1)
        var qh = 0
        var qt = 0
        queue[qt++] = s
        level[s] = 0
        while (qh < qt) {
            val u = queue[qh++]
            var e = head[u]
            while (e != -1) {
                val v = to[e]
                if (cap[e] > 0 && level[v] < 0) {
                    level[v] = level[u] + 1
                    queue[qt++] = v
                }
                e = next[e]
            }
        }
        return level[t] >= 0
    }

    private fun blocking(s: Int, t: Int): Int {
        var total = 0
        while (true) {
            var u = s
            var depth = 0
            while (u != t) {
                var advanced = false
                while (cursor[u] != -1) {
                    val e = cursor[u]
                    val v = to[e]
                    if (cap[e] > 0 && level[v] == level[u] + 1) {
                        pathEdge[depth++] = e
                        u = v
                        advanced = true
                        break
                    }
                    cursor[u] = next[e]
                }
                if (!advanced) {
                    if (u == s) return total
                    level[u] = -1
                    depth--
                    val back = pathEdge[depth]
                    u = to[back xor 1]
                    cursor[u] = next[cursor[u]]
                }
            }
            var f = Int.MAX_VALUE
            for (i in 0 until depth) f = minOf(f, cap[pathEdge[i]])
            for (i in 0 until depth) {
                cap[pathEdge[i]] -= f
                cap[pathEdge[i] xor 1] += f
            }
            total += f
        }
    }

    /** Nodes reachable from [s] in the residual graph: the source side of a minimum cut after [run]. */
    fun reachable(s: Int): BooleanArray {
        val seen = BooleanArray(n)
        val stack = ArrayDeque<Int>()
        seen[s] = true
        stack.add(s)
        while (stack.isNotEmpty()) {
            val u = stack.removeLast()
            var e = head[u]
            while (e != -1) {
                val v = to[e]
                if (cap[e] > 0 && !seen[v]) { seen[v] = true; stack.add(v) }
                e = next[e]
            }
        }
        return seen
    }
}
