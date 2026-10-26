package com.radx.app.ui

import com.radx.app.data.ProxyNode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class NodeSortTest {

    private fun node(name: String) = ProxyNode(
        tag = name,
        name = name,
        type = "vless",
        outbound = buildJsonObject {
            put("type", "vless")
            put("tag", name)
        },
    )

    private val nodes = listOf(node("c"), node("a"), node("b"))
    private val delays = mapOf<String, Int?>("a" to 320, "b" to 90, "c" to null)

    private fun List<ProxyNode>.names() = map { it.name }

    @Test
    fun `default keeps the imported order`() {
        assertEquals(listOf("c", "a", "b"), nodes.sortedByPing(delays, NodeSort.DEFAULT).names())
    }

    @Test
    fun `fastest puts measured delays first, fastest of all at the top`() {
        assertEquals(listOf("b", "a", "c"), nodes.sortedByPing(delays, NodeSort.FASTEST).names())
    }

    @Test
    fun `slowest inverts the delay order but keeps untested nodes last`() {
        assertEquals(listOf("a", "b", "c"), nodes.sortedByPing(delays, NodeSort.SLOWEST).names())
    }

    @Test
    fun `name sorts alphabetically`() {
        assertEquals(listOf("a", "b", "c"), nodes.sortedByPing(delays, NodeSort.NAME).names())
    }

    @Test
    fun `sort button cycles through every mode and wraps around`() {
        var sort = NodeSort.DEFAULT
        val seen = mutableListOf(sort)
        repeat(NodeSort.entries.size - 1) {
            sort = sort.next
            seen += sort
        }
        assertEquals(NodeSort.entries.toList(), seen)
        assertEquals(NodeSort.DEFAULT, sort.next)
    }

    @Test
    fun `a node with no delay entry stays last in a delay sort`() {
        val untested = nodes + node("d")
        assertEquals(
            listOf("b", "a", "c", "d"),
            untested.sortedByPing(delays, NodeSort.FASTEST).names(),
        )
    }
}
