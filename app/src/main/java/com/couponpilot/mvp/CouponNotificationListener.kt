package com.couponpilot.mvp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CouponNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val supportedPackages = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "net.one97.paytm",
        "com.dreamplug.androidapp",
        "in.amazon.mShop.android.shopping",
        "com.flipkart.android"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in supportedPackages) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val raw = listOf(title, text, bigText).filter { it.isNotBlank() }.distinct().joinToString(". ")
        if (!looksLikeCoupon(raw)) return

        CouponParser.parse(raw, sbn.packageName)?.let { coupon ->
            scope.launch { CouponDatabase.get(applicationContext).couponDao().insert(coupon) }
        }
    }

    private fun looksLikeCoupon(text: String): Boolean = listOf(
        "coupon", "cashback", "% off", "discount", "promo code", "offer"
    ).any { text.contains(it, ignoreCase = true) }
}
