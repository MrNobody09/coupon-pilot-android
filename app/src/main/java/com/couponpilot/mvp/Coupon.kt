package com.couponpilot.mvp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coupons")
data class Coupon(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val code: String,
    val discountType: String,
    val discountValue: Double,
    val maximumDiscount: Double?,
    val minimumSpend: Double,
    val paymentMethod: String,
    val expiresAtEpochMillis: Long?,
    val sourceApp: String,
    val rawText: String,
    val capturedAtEpochMillis: Long = System.currentTimeMillis()
)

data class CouponMatch(
    val coupon: Coupon,
    val estimatedSaving: Double,
    val eligible: Boolean,
    val reason: String
)
