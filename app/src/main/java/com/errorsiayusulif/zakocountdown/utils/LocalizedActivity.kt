// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/LocalizedActivity.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity

/**
 * Base class for every Activity in the app.
 *
 * It forces the resources of the Activity to the language chosen in-app
 * (see [LocaleHelper]), which is what makes the language switch take effect
 * immediately on Android versions without platform per-app language support.
 */
abstract class LocalizedActivity : AppCompatActivity() {

    private var localizedContext: Context? = null

    override fun attachBaseContext(newBase: Context) {
        val wrapped = LocaleHelper.wrap(newBase)
        localizedContext = wrapped
        super.attachBaseContext(wrapped)
    }

    override fun getResources(): Resources {
        // Guarantee that fragments, adapters and themes resolved from this Activity also see
        // the localized configuration even if something re-created the resources in between.
        return localizedContext?.resources ?: super.getResources()
    }
}
