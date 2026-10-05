package com.rizwan.tradingagentarmy.data.firebase

import com.rizwan.tradingagentarmy.data.firebase.FirebaseSync

/** Set by Hilt on app start so services without injection can reach the sync bridge. */
object FirebaseSyncHolder {
    @Volatile var sync: FirebaseSync? = null
}
