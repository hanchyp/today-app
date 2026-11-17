package com.farhan.today.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wallet")
data class Wallet(@PrimaryKey val id: String = newId(), val name: String)

@Entity(tableName = "wallet_allocation")
data class WalletAllocation(
    @PrimaryKey val id: String = newId(),
    val walletId: String,
    val amount: Long,
    val weekStart: Long,
)

data class WalletBalance(val id: String, val name: String, val balance: Long, val allocated: Long)

@Dao
interface WalletDao {
    @Insert suspend fun create(wallet: Wallet)
    @Insert suspend fun allocate(allocation: WalletAllocation)

    @Query("""
        SELECT w.id, w.name,
            COALESCE((SELECT SUM(a.amount) FROM wallet_allocation a WHERE a.walletId = w.id), 0) -
            COALESCE((SELECT SUM(t.amount) FROM money_transaction t WHERE t.walletId = w.id
                AND t.kind = 'EXPENSE' AND t.deletedAt IS NULL), 0) AS balance,
            COALESCE((SELECT SUM(a.amount) FROM wallet_allocation a WHERE a.walletId = w.id
                AND a.weekStart = :weekStart), 0) AS allocated
        FROM wallet w ORDER BY w.name
    """)
    fun balances(weekStart: Long): Flow<List<WalletBalance>>

    @Query("""
        SELECT COALESCE((SELECT SUM(amount) FROM money_transaction WHERE kind = 'INCOME' AND walletFunding = 1 AND deletedAt IS NULL), 0)
            - COALESCE((SELECT SUM(amount) FROM wallet_allocation), 0)
    """)
    fun unallocated(): Flow<Long>
}
