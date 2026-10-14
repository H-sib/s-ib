package com.hsib.sib.ui

import android.content.Context
import androidx.room.Room
import com.hsib.sib.Ready
import com.hsib.sib.data.SibDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/** A real [Ready] on an in-memory database, for tests that need the screens that talk to the store. */
object TestReady {
    fun create(context: Context, scope: CoroutineScope): Ready {
        val db = Room.inMemoryDatabaseBuilder(context, SibDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        val ready = Ready(context, db, ByteArray(32) { it.toByte() }, scope)
        runBlocking { ready.init() }
        return ready
    }
}
