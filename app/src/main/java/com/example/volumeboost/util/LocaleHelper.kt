package com.example.volumeboost.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    fun applyAppLocale(langCode: String) {
        val locales = when (langCode) {
            "ru" -> LocaleListCompat.forLanguageTags("ru")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList() // "system" -> следовать языку системы
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun isRussianActive(context: Context, configuredLangCode: String): Boolean {
        return when (configuredLangCode) {
            "ru" -> true
            "en" -> false
            else -> {
                val sysLang = context.resources.configuration.locales.get(0)?.language ?: Locale.getDefault().language
                sysLang.startsWith("ru", ignoreCase = true) || sysLang.startsWith("uk", ignoreCase = true)
            }
        }
    }

    /**
     * Возвращает Context с принудительно выставленной локалью для Foreground Service и Суперострова,
     * чтобы уведомления переводились в ту же миллисекунду, когда пользователь переключил язык.
     */
    fun getLocalizedContext(base: Context, configuredLangCode: String): Context {
        val targetLocale = when (configuredLangCode) {
            "ru" -> Locale("ru")
            "en" -> Locale.ENGLISH
            else -> base.resources.configuration.locales.get(0) ?: Locale.getDefault()
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(targetLocale)
        return base.createConfigurationContext(config)
    }
}
