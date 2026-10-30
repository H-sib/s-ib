package com.hsib.sib.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A person's data must survive an update (APP_QUALITY_STANDARD.txt, lesson L12). This builds the real version 1 database from
 * its exported schema, fills it, then opens it with the real database class, which makes Room run its own migration to the
 * current version. Every row must still be there and the new tables must exist. Add a case for every version that ships.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    /** Creates a database file exactly as version [version] of the app would have, from the schema Room exported then. */
    private fun createVersion(name: String, version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = context.assets.open("com.hsib.sib.data.SibDatabase/$version.json").bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("database")
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs(); it.delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = e.optJSONArray("indices")
            if (indices != null) for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '${schema.getString("identityHash")}')")
        fill(db)
        db.version = version
        db.close()
    }

    @Test
    fun version1KeepsEveryRowWhenItBecomesVersion2() {
        createVersion("migration-test", 1) { db ->
            db.execSQL(
                "INSERT INTO obligation VALUES ('o1','Signals lab report','notes','ASSIGNMENT','DEADLINE','2026-11-18T23:59','UTC','DATE_TIME',0," +
                    "NULL,NULL,'HIGH',60,120,200,'ACTIVE',NULL,45,1000,2000,NULL,'WORK','NONE',NULL,'3,1','','',NULL)",
            )
            db.execSQL("INSERT INTO setting VALUES ('theme','NEON')")
            db.execSQL("INSERT INTO dismissed VALUES ('fingerprint-1', 5)")
            db.execSQL("INSERT INTO seal VALUES ('o1', 0, 1000, 'created', '', 'Signals lab report | x', 'USER', 'created', 'genesis', 'hash0')")
        }
        val db = Room.databaseBuilder(context, SibDatabase::class.java, "migration-test").allowMainThreadQueries().build()
        try {
            runBlocking {
                val o = db.obligations().get("o1")!!
                assertEquals("Signals lab report", o.title)
                assertEquals("HIGH", o.importance)
                assertEquals(45, o.progressMin)
                assertEquals("3,1", o.leadDays)
                assertEquals("NEON", db.settings().all().single { it.key == "theme" }.value)
                assertEquals(listOf("fingerprint-1"), db.dismissed().fingerprints())
                assertEquals("hash0", db.seals().forObligation("o1").single().hash)
                // The tables added for shared deadlines exist and start empty.
                assertEquals(0, db.rooms().all().size)
                assertEquals(0, db.roomKeys().forRoom("none").size)
                assertEquals(0, db.roomMembers().forRoom("none").size)
                assertEquals(0, db.roomMessages().forRoom("none").size)
                assertEquals(0, db.blocked().all().size)
            }
        } finally {
            db.close()
        }
    }
}
