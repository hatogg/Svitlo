package ua.x44to.svitlo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

object Links {
    fun open(ctx: Context, url: String) = start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun start(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // наступний варіант
            }
        }
        return false
    }
}
