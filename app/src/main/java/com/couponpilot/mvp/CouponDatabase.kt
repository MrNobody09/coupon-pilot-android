package com.couponpilot.mvp

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons ORDER BY capturedAtEpochMillis DESC")
    fun observeAll(): Flow<List<Coupon>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coupon: Coupon)

    @Query("DELETE FROM coupons WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM coupon_feedback ORDER BY createdAtEpochMillis DESC")
    fun observeFeedback(): Flow<List<CouponFeedback>>

    @Query("SELECT * FROM improvement_proposals ORDER BY createdAtEpochMillis DESC")
    fun observeProposals(): Flow<List<ImprovementProposal>>

    @Insert
    suspend fun insertFeedback(feedback: CouponFeedback)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProposal(proposal: ImprovementProposal)

    @Query("SELECT COUNT(*) FROM improvement_proposals WHERE proposalType = :type AND rulePayload = :payload AND status IN ('PENDING', 'APPROVED')")
    suspend fun existingProposalCount(type: String, payload: String): Int

    @Query("UPDATE improvement_proposals SET status = :status, reviewedAtEpochMillis = :reviewedAt WHERE id = :id")
    suspend fun reviewProposal(id: Long, status: String, reviewedAt: Long = System.currentTimeMillis())
}

@Database(
    entities = [Coupon::class, CouponFeedback::class, ImprovementProposal::class],
    version = 2,
    exportSchema = false
)
abstract class CouponDatabase : RoomDatabase() {
    abstract fun couponDao(): CouponDao
    abstract fun learningDao(): LearningDao

    companion object {
        @Volatile private var instance: CouponDatabase? = null

        fun get(context: Context): CouponDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CouponDatabase::class.java,
                "coupon-pilot.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
