package com.radx.app.ui

import com.radx.app.R
import com.radx.app.data.ProxyNode

/** Ordering of the node list; the sort button cycles through these. */
enum class NodeSort(val labelRes: Int) {
    DEFAULT(R.string.sort_default),
    FASTEST(R.string.sort_fastest),
    SLOWEST(R.string.sort_slowest),
    NAME(R.string.sort_name),
    ;

    val next: NodeSort get() = entries[(ordinal + 1) % entries.size]
}

/**
 * Orders [this] by the measured delays.
 *
 * Nodes without a delay (not tested yet, or timed out) stay together at the end of a
 * delay sort, so a running test does not shuffle the list on every result.
 */
fun List<ProxyNode>.sortedByPing(delays: Map<String, Int?>, sort: NodeSort): List<ProxyNode> =
    when (sort) {
        NodeSort.DEFAULT -> this

        NodeSort.NAME -> sortedBy { it.name.lowercase() }

        NodeSort.FASTEST -> sortedWith(
            compareBy<ProxyNode> { delays[it.tag] == null }
                .thenBy { delays[it.tag] ?: 0 }
                .thenBy { it.name.lowercase() },
        )

        NodeSort.SLOWEST -> sortedWith(
            compareBy<ProxyNode> { delays[it.tag] == null }
                .thenByDescending { delays[it.tag] ?: 0 }
                .thenBy { it.name.lowercase() },
        )
    }
