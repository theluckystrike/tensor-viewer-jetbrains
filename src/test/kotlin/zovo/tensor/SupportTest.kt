package zovo.tensor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupportTest {
    private val ints = HashMap<String, Int>()
    private val longs = HashMap<String, Long>()
    private var now = 1_800_000_000_000L
    private val saved = RatingState.store
    private val savedClock = RatingState.clock

    @Before fun setUp() {
        RatingState.store = object : RatingState.Store {
            override fun getInt(key: String) = ints[key] ?: 0
            override fun setInt(key: String, v: Int) { ints[key] = v }
            override fun getLong(key: String) = longs[key] ?: 0L
            override fun setLong(key: String, v: Long) { longs[key] = v }
        }
        RatingState.clock = { now }
    }

    @After fun tearDown() { RatingState.store = saved; RatingState.clock = savedClock }

    @Test fun rateLinkOnlyAfterThreeGoodOpensAndNotSoonAfterAFailure() {
        assertFalse(RatingState.shouldShowLink())
        RatingState.report(true); RatingState.report(true)
        assertFalse(RatingState.shouldShowLink())
        RatingState.report(true)
        assertTrue(RatingState.shouldShowLink())
        RatingState.report(false)
        assertFalse(RatingState.shouldShowLink())
        now += 23L * 3600 * 1000
        assertFalse(RatingState.shouldShowLink())
        now += 3600 * 1000
        assertTrue(RatingState.shouldShowLink())
    }

    @Test fun footerLinksAndOrder() {
        val plain = Support.footerHtml(false, "https://plugins.jetbrains.com/plugin/1/reviews")
        assertFalse(plain, plain.contains("Rate on"))
        val withRate = Support.footerHtml(true, "https://plugins.jetbrains.com/plugin/1/reviews")
        assertTrue(withRate, withRate.startsWith("<div class=\"zovo-support\">Support and more tools: <a href=\"https://plugins.jetbrains.com/plugin/1/reviews\">Rate on JetBrains Marketplace</a>"))
        assertFalse(Support.footerHtml(true, null).contains("Rate on"))
        assertNull(Support.rateUrl(""))
        assertEquals("https://plugins.jetbrains.com/plugin/42/reviews", Support.rateUrl("42"))
        for (u in listOf("https://github.com/theluckystrike/tensor-viewer-jetbrains", "https://github.com/sponsors/theluckystrike",
                "itemName=zovo.tensor-viewer", "plugin/35879", "plugin/35878")) assertTrue(u, plain.contains(u))
        assertFalse("no PayPal while funding.json has none", plain.contains("paypal", ignoreCase = true))
        assertTrue(plain.contains("NetCDF, HDF5 &amp; NumPy Viewer"))
    }
}
