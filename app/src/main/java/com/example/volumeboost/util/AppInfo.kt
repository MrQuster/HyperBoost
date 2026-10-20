package com.example.volumeboost.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.volumeboost.BuildConfig
import com.example.volumeboost.R

object AppInfo {
    const val APP_NAME: String = "HyperBoost"
    val AUTHOR_NAME: String = BuildConfig.AUTHOR_NAME
    val GITHUB_USER: String = BuildConfig.GITHUB_USER
    val GITHUB_PROFILE_URL: String = BuildConfig.GITHUB_PROFILE_URL
    val REPO_URL: String = BuildConfig.REPO_URL
    val RELEASES_URL: String = BuildConfig.RELEASES_URL
    val VERSION_NAME: String = BuildConfig.VERSION_NAME
    val VERSION_CODE: Int = BuildConfig.VERSION_CODE
    val COPYRIGHT_YEAR: String = BuildConfig.COPYRIGHT_YEAR

    fun reportHeaderLine(): String = "$APP_NAME $VERSION_NAME by $AUTHOR_NAME"

    fun openUrlSafely(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.err_no_browser),
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.err_no_browser),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
