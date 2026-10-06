package com.rizwan.tradingagentarmy

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * ContentProviders are created before Application.onCreate, so this is the earliest
 * safe place to install [CrashCatcher] — it also catches failures inside Hilt
 * field injection (which runs during super.onCreate()).
 */
class CrashCatcherProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { CrashCatcher.install(it) }
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0
}
