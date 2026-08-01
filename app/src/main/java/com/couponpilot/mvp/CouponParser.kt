package com.couponpilot.mvp

object CouponParser {
    private val percent = Regex("(\\d{1,2})%\\s*(?:off|discount)", RegexOption.IGNORE_CASE)
    private val flat = Regex("(?:₹|rs\\.?|inr)\\s*(\\d+)\\s*(?:off|discount|cashback)", RegexOption.IGNORE_CASE)
    private val minSpend = Regex("(?:min(?:imum)?\\s*(?:spend|order)|on\\s+(?:orders?|payment)\\s+(?:above|of))\\s*(?:₹|rs\\.?|inr)?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val maxDiscount = Regex("(?:up\\s*to|max(?:imum)?)\\s*(?:₹|rs\\.?|inr)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val code = Regex("(?:code|use)[:\\s-]*([A-Z0-9]{4,20})", RegexOption.IGNORE_CASE)

    fun parse(raw: String, sourceApp: String): Coupon? {
        val p = percent.find(raw)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val f = flat.find(raw)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (p == null && f == null) return null

        val inferredMerchant = listOf("Swiggy", "Zomato", "Amazon", "Flipkart", "Myntra", "Uber", "Ola", "Blinkit", "Zepto")
            .firstOrNull { raw.contains(it, ignoreCase = true) } ?: "Any merchant"

        return Coupon(
            merchant = inferredMerchant,
            code = code.find(raw)?.groupValues?.getOrNull(1)?.uppercase().orEmpty(),
            discountType = if (p != null) "PERCENT" else "FLAT",
            discountValue = p ?: f ?: return null,
            maximumDiscount = maxDiscount.find(raw)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
            minimumSpend = minSpend.find(raw)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            paymentMethod = inferPaymentMethod(raw),
            expiresAtEpochMillis = null,
            sourceApp = sourceApp,
            rawText = raw
        )
    }

    private fun inferPaymentMethod(raw: String): String = when {
        raw.contains("UPI", true) -> "UPI"
        raw.contains("credit card", true) -> "Credit card"
        raw.contains("debit card", true) -> "Debit card"
        raw.contains("wallet", true) -> "Wallet"
        else -> ""
    }
}
