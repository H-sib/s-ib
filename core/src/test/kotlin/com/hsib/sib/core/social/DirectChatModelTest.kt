package com.hsib.sib.core.social

import com.hsib.sib.core.model.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectChatModelTest {
    @Test fun usernamesAreCleanedAndChecked() {
        assertEquals("rose.noir_1", SocialRules.cleanUsername("  @Rose.Noir_1 "))
        assertNull(SocialRules.cleanUsername("ab"))
        assertNull(SocialRules.cleanUsername("has space"))
        assertNull(SocialRules.cleanUsername("a".repeat(21)))
        assertNull(SocialRules.cleanUsername("bad!char"))
    }

    @Test fun theEmailHashIsStableAndNotTheAddress() {
        val h = SocialRules.emailHash(" Someone@Example.COM ")
        assertEquals(h, SocialRules.emailHash("someone@example.com"))
        assertEquals(64, h.length)
        assertFalse(h.contains("someone"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SocialRules.emailHash("ABC"))
    }

    @Test fun emailsAreToldFromUsernames() {
        assertTrue(SocialRules.looksLikeEmail("a@b.co"))
        assertFalse(SocialRules.looksLikeEmail("rose.noir"))
        assertFalse(SocialRules.looksLikeEmail("a b@c.com"))
    }

    @Test fun aChatPayloadMakesAChatOnlyEntry() {
        val p = SharedPayload.forChat()
        assertTrue(p.chat)
        val back = SharedPayload.fromBytes(p.toBytes())
        assertNotNull(back)
        assertEquals(Space.CHAT, back!!.toNewObligation("x").space)
        assertNull(back.toNewObligation("x").due)
    }

    @Test fun oldPayloadsStillMeanADeadline() {
        val old = """{"title":"Report","category":"OTHER"}""".toByteArray()
        assertEquals(Space.WORK, SharedPayload.fromBytes(old)!!.toNewObligation("x").space)
    }
}
