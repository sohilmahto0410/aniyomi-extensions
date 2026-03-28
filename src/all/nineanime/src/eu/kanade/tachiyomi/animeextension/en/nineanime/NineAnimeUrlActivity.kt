package eu.kanade.tachiyomi.animeextension.en.nineanime

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Handles deep links of the form:
 *   https://9animetv.to/watch/<slug>-<id>
 *
 * Tapping a 9animetv.to link in the browser routes here via the intent filter
 * in AndroidManifest.xml. We forward to Aniyomi's main activity with a search
 * query so it opens the correct anime detail page.
 */
class NineAnimeUrlActivity : Activity() {

    private val TAG = "NineAnimeUrlActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data
        if (data != null && data.pathSegments.isNotEmpty()) {
            // Path: /watch/<slug>-<numericId>
            // We use the slug (minus the trailing ID) as a search query.
            val lastSegment = data.lastPathSegment ?: ""
            // Strip trailing numeric id so "one-piece-100" becomes "one piece"
            val slug = lastSegment.replace(Regex("-\\d+$"), "").replace("-", " ")

            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.SEARCH"
                putExtra("query", slug)
                putExtra("filter", packageName)
            }
            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Could not open Aniyomi: $e")
            }
        } else {
            Log.e(TAG, "No usable data in intent: $data")
        }

        finish()
    }
}
