package zovo.tensor

import com.intellij.ide.util.PropertiesComponent

/**
 * Quiet rating link, on the rating ask v2 rules (factory/sprint/rating): no dialog and no popup, ever. The
 * "Rate on JetBrains Marketplace" link shows in the footer row only after 3 files opened without an error, and not
 * within 24 hours of a file that failed to open. Kept in the IDE settings; no network, no telemetry.
 */
object RatingState {
    const val MIN_GOOD_OPENS = 3
    const val QUIET_AFTER_FAILURE_MS = 24L * 60 * 60 * 1000
    private const val KEY_GOOD = "zovo.tensor.rating.goodOpens"
    private const val KEY_FAIL = "zovo.tensor.rating.lastFailure"

    interface Store { fun getInt(key: String): Int; fun setInt(key: String, v: Int); fun getLong(key: String): Long; fun setLong(key: String, v: Long) }

    private object IdeStore : Store {
        private fun p() = PropertiesComponent.getInstance()
        override fun getInt(key: String) = p().getInt(key, 0)
        override fun setInt(key: String, v: Int) = p().setValue(key, v, 0)
        override fun getLong(key: String) = p().getLong(key, 0L)
        override fun setLong(key: String, v: Long) = p().setValue(key, v.toString())
    }

    @Volatile var store: Store = IdeStore
    @Volatile var clock: () -> Long = System::currentTimeMillis

    fun report(ok: Boolean) {
        val s = store
        if (ok) s.setInt(KEY_GOOD, minOf(1_000_000, s.getInt(KEY_GOOD) + 1)) else s.setLong(KEY_FAIL, clock())
    }

    fun shouldShowLink(): Boolean {
        val s = store
        val lastFail = s.getLong(KEY_FAIL)
        return s.getInt(KEY_GOOD) >= MIN_GOOD_OPENS && (lastFail == 0L || clock() - lastFail >= QUIET_AFTER_FAILURE_MS)
    }
}
