package com.couponpilot.mvp

import java.util.Locale
import kotlin.math.min

object CouponEngine {
    fun rank(
        coupons: List<Coupon>,
        merchant: String,
        amount: Double,
        paymentMethod: String,
        now: Long = System.currentTimeMillis()
    ): List<CouponMatch> = coupons.map { coupon ->
        val merchantOk = merchant.isBlank() || coupon.merchant.contains(merchant, ignoreCase = true) ||
            merchant.contains(coupon.merchant, ignoreCase = true)
        val amountOk = amount >= coupon.minimumSpend
        val paymentOk = coupon.paymentMethod.isBlank() || paymentMethod.isBlank() ||
            coupon.paymentMethod.contains(paymentMethod, ignoreCase = true) ||
            paymentMethod.contains(coupon.paymentMethod, ignoreCase = true)
        val expiryOk = coupon.expiresAtEpochMillis == null || coupon.expiresAtEpochMillis > now

        val eligible = merchantOk && amountOk && paymentOk && expiryOk
        val saving = if (!eligible) 0.0 else when (coupon.discountType) {
            "PERCENT" -> min(amount * coupon.discountValue / 100.0, coupon.maximumDiscount ?: Double.MAX_VALUE)
            else -> min(coupon.discountValue, amount)
        }
        val reason = when {
            !merchantOk -> "Not valid for this merchant"
            !amountOk -> "Minimum spend is ₹${coupon.minimumSpend.toInt()}"
            !paymentOk -> "Requires ${coupon.paymentMethod}"
            !expiryOk -> "Expired"
            else -> "Estimated saving ₹${String.format(Locale.US, "%.0f", saving)}"
        }
        CouponMatch(coupon, saving, eligible, reason)
    }.sortedWith(compareByDescending<CouponMatch> { it.eligible }.thenByDescending { it.estimatedSaving })
}
