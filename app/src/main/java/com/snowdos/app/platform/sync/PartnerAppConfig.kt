package com.snowdos.app.platform.sync

import android.net.Uri

/**
 * Shared protocol constants between SnowDo's (`com.snowdos.app`) and SnowSplit (`com.snowsplit.app`).
 */
object PartnerAppConfig {
    const val OWN_PACKAGE = "com.snowdos.app"
    const val PARTNER_PACKAGE = "com.snowsplit.app"
    const val SYNC_PERMISSION = "com.snowapps.permission.THEME_SYNC"
    const val SYNC_ACTION = "com.snowapps.action.APPEARANCE_SYNC"
    const val EXTRA_PAYLOAD_JSON = "payload_json"

    const val OWN_AUTHORITY = "com.snowdos.app.syncprovider"
    const val PARTNER_AUTHORITY = "com.snowsplit.app.syncprovider"
    const val PATH_APPEARANCE = "appearance"
    const val PATH_BACKGROUND = "background"
    const val COLUMN_PAYLOAD_JSON = "payload_json"

    const val OWN_BACKGROUND_CONTENT_URI = "content://$OWN_AUTHORITY/$PATH_BACKGROUND"
    val PARTNER_APPEARANCE_URI: Uri = Uri.parse("content://$PARTNER_AUTHORITY/$PATH_APPEARANCE")
}
