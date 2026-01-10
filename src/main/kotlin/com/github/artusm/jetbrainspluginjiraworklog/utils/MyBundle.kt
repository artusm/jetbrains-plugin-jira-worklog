package com.github.artusm.jetbrainspluginjiraworklog.utils

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Message bundle for internationalization support.
 * Supports English (default) and Russian translations.
 */
object MyBundle : DynamicBundle("messages.MyBundle") {
    
    @Nls
    fun message(@PropertyKey(resourceBundle = "messages.MyBundle") key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
