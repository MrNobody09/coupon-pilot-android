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

@Database(entities = [Coupon::class], version = 1, exportSchema = false)
abstract class CouponDatabase : RoomDatabase() {
    abstract fun couponDao(): CouponDao

    companion object {
        @Volatile private var instance: CouponDatabase? = null

        fun get(context: Context): CouponDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CouponDatabase::class.java,
                "coupon-pilot.db"
            ).build().also { instance = it }
        }
    }
}
