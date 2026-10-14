package com.hsib.sib.ui

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.hsib.sib.Ready
import com.hsib.sib.core.model.Limits
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Tier
import com.hsib.sib.platform.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The store, the importer and the route check against hostile input, on a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StoreSecurityTest {
    private fun ready(): Ready = TestReady.create(ApplicationProvider.getApplicationContext(), CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun savingCleansAndBoundsEveryTextField() = runBlocking {
        val r = ready()
        r.store.create(Obligation(id = "a", title = "T".repeat(500) + "\r\nX", notes = "n".repeat(20_000) + "\u0000", person = "P\u0000Q", relation = "r".repeat(300)))
        val stored = r.store.obligation("a")!!
        assertEquals(Limits.TITLE, stored.title.length)
        assertTrue(stored.notes.length <= Limits.NOTES)
        assertEquals("P Q", stored.person)
        assertEquals(Limits.RELATION, stored.relation.length)
        assertFalse(stored.title.any { it.isISOControl() })
    }

    @Test
    fun aRestoredOrSharedTitleCannotBeEmpty() = runBlocking {
        val r = ready()
        r.store.create(Obligation(id = "b", title = " \n "))
        assertEquals("Untitled", r.store.obligation("b")!!.title)
    }

    @Test
    fun pendingSuggestionsAreCappedAndTheMostLikelySurvive() = runBlocking {
        val r = ready()
        val total = Limits.MAX_PENDING + 50
        val flood = (1..total).map { Fixtures.candidate("c$it", "Item $it", Tier.SUGGEST, confidence = it / 1000.0) }
        r.store.saveCandidates(flood, 30)
        val kept = r.store.storedCandidates()
        assertEquals(Limits.MAX_PENDING, kept.size)
        assertTrue(kept.any { it.candidate.id == "c$total" })
        assertFalse(kept.any { it.candidate.id == "c1" })
    }

    @Test
    fun importsRefuseAddressesThatAreNotContentSources() = runBlocking {
        val r = ready()
        assertEquals("That source is not supported.", r.importer.fromUri(Uri.parse("file:///data/data/com.hsib.sib/files/vault.bin")).error)
        assertEquals("That source is not supported.", r.importer.fromUri(Uri.parse("http://example.com/a.pdf")).error)
    }

    @Test
    fun onlyKnownScreensAreAcceptedFromAnIntent() {
        assertEquals("review", Notifier.safeRoute("review"))
        assertEquals("item/3f2a-11", Notifier.safeRoute("item/3f2a-11"))
        assertEquals("close/item/abc-123", Notifier.safeRoute("close/item/abc-123"))
        for (bad in listOf("settings/backup", "restore", "item/", "item/../x", "item/a b", "close/edit?id=1", "home\nreview", "", "item/" + "a".repeat(65))) {
            assertNull("accepted: $bad", Notifier.safeRoute(bad))
        }
        assertNull(Notifier.safeRoute(null))
    }
}
