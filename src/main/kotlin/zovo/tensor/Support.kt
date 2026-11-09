package zovo.tensor

/**
 * "Support and more tools" row at the bottom of every viewer tab (owner rule): GitHub, GitHub Sponsors and PayPal
 * (factory/sprint/funding.json). The rating link comes first, and only once it is earned.
 */
object Support {
    const val REPO = "https://github.com/theluckystrike/tensor-viewer-jetbrains"

    const val PLUGIN_XML_ID = "zovo.tensor-viewer"

    /** Numeric JetBrains Marketplace id, known after the first upload. Empty: the rating link uses the xmlId URL, which redirects to the plugin page. */
    const val MARKETPLACE_ID = ""

    val LINKS = listOf(
        "GitHub" to REPO,
        "Sponsor on GitHub" to "https://github.com/sponsors/theluckystrike",
        "PayPal" to "https://www.paypal.com/paypalme/najzzz",
        "NumPy & GGUF Viewer for VS Code" to "https://marketplace.visualstudio.com/items?itemName=zovo.tensor-viewer",
        "NetCDF, HDF5 & NumPy Viewer" to "https://plugins.jetbrains.com/plugin/35879-netcdf-hdf5--numpy-viewer",
        "DOCX & PPTX Viewer" to "https://plugins.jetbrains.com/plugin/35878-docx--pptx-viewer",
        "More Zovo tools" to "https://github.com/theluckystrike",
    )

    fun rateUrl(id: String = MARKETPLACE_ID): String? = if (id.isBlank()) "https://plugins.jetbrains.com/plugin/index?xmlId=$PLUGIN_XML_ID" else "https://plugins.jetbrains.com/plugin/$id/reviews"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")

    fun footerHtml(): String = footerHtml(RatingState.shouldShowLink(), rateUrl())

    fun footerHtml(showRate: Boolean, rateUrl: String?): String {
        val links = ArrayList<Pair<String, String>>()
        if (showRate && rateUrl != null) links.add("Rate on JetBrains Marketplace" to rateUrl)
        links.addAll(LINKS)
        return links.joinToString("<span class=\"sep\">·</span>", "<div class=\"zovo-support\">Support and more tools: ", "</div>") { (label, url) ->
            "<a href=\"" + url + "\">" + esc(label) + "</a>"
        }
    }
}
