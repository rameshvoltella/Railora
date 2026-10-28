package com.ramz.railora.data.railmitra

import com.ramz.railora.domain.model.PnrStatus
import com.ramz.railora.domain.model.PnrUnavailable
import com.ramz.railora.domain.source.PnrSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * RailMitra PNR status: its form is a plain GET, `https://www.railmitra.com/pnr-status?pnr={pnr}`,
 * and the page is rendered on the server.
 *
 * Not finished: with a made-up PNR (all that was available when this was written, during the
 * railways' nightly downtime) the page comes back unchanged, so the layout of a real result
 * hasn't been seen yet. [RailMitraPnrParser.parse] needs it to be filled in from a saved page of
 * a real PNR; until then it says so rather than guessing.
 */
class RailMitraPnr : PnrSource {

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
    }

    override suspend fun pnrStatus(pnr: String): PnrStatus = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect("https://www.railmitra.com/pnr-status?pnr=$pnr")
            .userAgent(UA).referrer("https://www.railmitra.com/pnr-status").timeout(30_000).maxBodySize(0).get()
        RailMitraPnrParser.parse(doc, pnr)
    }
}

object RailMitraPnrParser {

    fun parse(doc: Document, pnr: String): PnrStatus {
        doc.selectFirst(".alert-danger, .text-danger:not(.d-none)")?.text()?.takeIf { it.isNotBlank() }
            ?.let { throw PnrUnavailable(it) }
        throw PnrUnavailable(
            "Reading PNR status from RailMitra isn't ready yet. Choose ConfirmTkt in Settings for now."
        )
    }
}
