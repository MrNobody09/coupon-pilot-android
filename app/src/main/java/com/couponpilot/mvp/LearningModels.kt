package com.couponpilot.mvp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coupon_feedback")
data class CouponFeedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val couponId: Long,
    val merchant: String,
    val sourceApp: String,
    val outcome: String,
    val reason: String,
    val transactionAmount: Double,
    val paymentMethod: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "improvement_proposals")
data class ImprovementProposal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val proposalType: String,
    val title: String,
    val description: String,
    val evidence: String,
    val confidence: Double,
    val status: String = "PENDING",
    val rulePayload: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val reviewedAtEpochMillis: Long? = null
)
