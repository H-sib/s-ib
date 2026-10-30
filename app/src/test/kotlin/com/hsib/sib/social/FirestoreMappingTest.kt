package com.hsib.sib.social

import com.google.firebase.Timestamp
import com.hsib.sib.core.social.MessageKind
import com.hsib.sib.core.social.Profile
import com.hsib.sib.core.social.Report
import com.hsib.sib.core.social.ReportReason
import com.hsib.sib.core.social.RoomRole
import com.hsib.sib.core.social.SocialLimits
import com.hsib.sib.social.firebase.Fields
import com.hsib.sib.social.firebase.FirestoreMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The documents that come back from the server are untrusted. These tests feed the mapping good ones, damaged ones and
 * hostile ones and check that a bad document is skipped whole, never half read.
 */
class FirestoreMappingTest {
    private val key43 = "A".repeat(43)

    private fun room(vararg over: Pair<String, Any?>) = mapOf<String, Any?>(
        Fields.OWNER_UID to "ana", Fields.KEY_VERSION to 2L, Fields.REV to 3L, Fields.SEALED_PAYLOAD to "sealed",
        Fields.MEMBER_COUNT to 4L, Fields.CREATED_AT to Timestamp(1_760_000_000, 500_000_000),
    ) + over

    @Test
    fun timestampsBecomeMilliseconds() {
        assertEquals(1_760_000_000_500L, FirestoreMapping.millis(Timestamp(1_760_000_000, 500_000_000)))
        assertEquals(42L, FirestoreMapping.millis(42))
        assertNull(FirestoreMapping.millis("yesterday"))
        assertNull(FirestoreMapping.millis(null))
    }

    @Test
    fun aGoodRoomIsRead() {
        val r = FirestoreMapping.room("r1", room())!!
        assertEquals("r1", r.id)
        assertEquals("ana", r.ownerUid)
        assertEquals(2, r.keyVersion)
        assertEquals(3L, r.rev)
        assertEquals(4, r.memberCount)
        assertEquals(1_760_000_000_500L, r.createdAtMs)
    }

    @Test
    fun aDamagedRoomIsSkippedWhole() {
        for (bad in listOf(
            room(Fields.OWNER_UID to 5),
            room(Fields.KEY_VERSION to 0L),
            room(Fields.KEY_VERSION to "two"),
            room(Fields.REV to 0L),
            room(Fields.SEALED_PAYLOAD to ""),
            room(Fields.SEALED_PAYLOAD to "x".repeat(SocialLimits.MAX_ENVELOPE_CHARS + 1)),
            room(Fields.MEMBER_COUNT to 99L),
            room(Fields.MEMBER_COUNT to -1L),
            room(Fields.MEMBER_COUNT to null),
        )) assertNull("accepted $bad", FirestoreMapping.room("r1", bad))
        assertNull(FirestoreMapping.room("r1", emptyMap()))
    }

    @Test
    fun aMemberIsReadWithOrWithoutADoneMark() {
        val base = mapOf<String, Any?>(
            Fields.ROLE to "MEMBER", Fields.DISPLAY_NAME to "Ben", Fields.JOINED_AT to Timestamp(100, 0),
            Fields.WRAPPED_KEY to "k", Fields.WRAPPED_KEY_VERSION to 1L, Fields.LAST_MESSAGE_AT to Timestamp(150, 0),
        )
        val m = FirestoreMapping.member("r1", "ben", base)!!
        assertEquals(RoomRole.MEMBER, m.role)
        assertNull(m.doneAtMs)
        assertEquals(100_000L, m.joinedAtMs)
        assertEquals(150_000L, m.lastMessageAtMs)
        assertEquals(200_000L, FirestoreMapping.member("r1", "ben", base + (Fields.DONE_AT to Timestamp(200, 0)))!!.doneAtMs)
        // A done mark written as null (the app clears it that way) reads as not done.
        assertNull(FirestoreMapping.member("r1", "ben", base + (Fields.DONE_AT to null))!!.doneAtMs)
        assertEquals(RoomRole.OWNER, FirestoreMapping.member("r1", "ana", base + (Fields.ROLE to "OWNER"))!!.role)
    }

    @Test
    fun aDamagedMemberIsSkipped() {
        val base = mapOf<String, Any?>(Fields.ROLE to "MEMBER", Fields.DISPLAY_NAME to "Ben")
        assertNotNull(FirestoreMapping.member("r1", "ben", base))
        assertNull(FirestoreMapping.member("r1", "ben", base + (Fields.ROLE to "ADMIN")))
        assertNull(FirestoreMapping.member("r1", "ben", base + (Fields.DISPLAY_NAME to "   ")))
        assertNull(FirestoreMapping.member("r1", "ben", base + (Fields.DISPLAY_NAME to "N".repeat(41))))
        assertNull(FirestoreMapping.member("r1", "ben", base + (Fields.DISPLAY_NAME to 7)))
    }

    @Test
    fun aMessageNeedsEveryFieldAndAKnownKind() {
        val base = mapOf<String, Any?>(
            Fields.SENDER_UID to "ben", Fields.CREATED_AT to Timestamp(5, 0), Fields.KIND to "TEXT",
            Fields.SEALED_BODY to "sealed", Fields.KEY_VERSION to 1L,
        )
        val m = FirestoreMapping.message("r1", "m1", base)!!
        assertEquals(MessageKind.TEXT, m.kind)
        assertEquals(5_000L, m.createdAtMs)
        assertEquals(MessageKind.SYSTEM, FirestoreMapping.message("r1", "m1", base + (Fields.KIND to "SYSTEM"))!!.kind)
        for (key in base.keys) assertNull("accepted without $key", FirestoreMapping.message("r1", "m1", base - key))
        assertNull(FirestoreMapping.message("r1", "m1", base + (Fields.KIND to "SHOUT")))
        assertNull(FirestoreMapping.message("r1", "m1", base + (Fields.SEALED_BODY to "")))
        // A message whose time is still pending on the server has no time yet, and is skipped until it has.
        assertNull(FirestoreMapping.message("r1", "m1", base + (Fields.CREATED_AT to null)))
    }

    @Test
    fun anInviteIsReadOnlyWhenComplete() {
        val base = mapOf<String, Any?>(
            Fields.ROOM_ID to "r1", Fields.CREATED_BY to "ana", Fields.EXPIRES_AT to Timestamp(9, 0), Fields.REVOKED to false,
            Fields.SEALED_KEY to "sealedkey", Fields.KEY_VERSION to 1L,
        )
        val i = FirestoreMapping.invite("ABCDEFGHJK", base)!!
        assertFalse(i.revoked)
        assertEquals(9_000L, i.expiresAtMs)
        assertTrue(FirestoreMapping.invite("ABCDEFGHJK", base + (Fields.REVOKED to true))!!.revoked)
        for (key in base.keys) assertNull("accepted without $key", FirestoreMapping.invite("ABCDEFGHJK", base - key))
        assertNull(FirestoreMapping.invite("ABCDEFGHJK", base + (Fields.REVOKED to "no")))
        assertNull(FirestoreMapping.invite("ABCDEFGHJK", base + (Fields.SEALED_KEY to "x".repeat(513))))
    }

    @Test
    fun aProfileNeedsANameAndAFullKey() {
        assertEquals(Profile("u", "Ana", key43), FirestoreMapping.profile("u", mapOf(Fields.DISPLAY_NAME to "Ana", Fields.PUBLIC_KEY to key43)))
        assertNull(FirestoreMapping.profile("u", mapOf(Fields.DISPLAY_NAME to "Ana", Fields.PUBLIC_KEY to "short")))
        assertNull(FirestoreMapping.profile("u", mapOf(Fields.DISPLAY_NAME to "", Fields.PUBLIC_KEY to key43)))
        assertNull(FirestoreMapping.profile("u", emptyMap()))
        assertEquals(setOf(Fields.DISPLAY_NAME, Fields.PUBLIC_KEY), FirestoreMapping.profileData(Profile("u", "Ana", key43)).keys)
    }

    @Test
    fun aReportCarriesTheQuoteOnlyWhenThereIsOne() {
        val withQuote = FirestoreMapping.reportData(Report("r1", "ben", "m1", ReportReason.SPAM, "detail", "quoted"), "ana")
        assertEquals("quoted", withQuote["quote"])
        assertEquals("SPAM", withQuote["reason"])
        assertEquals("ana", withQuote["reporterUid"])
        val without = FirestoreMapping.reportData(Report("r1", "ben", null, ReportReason.OTHER, "", null), "ana")
        assertFalse(without.containsKey("quote"))
        assertFalse(without.containsKey("messageId"))
    }

    @Test
    fun theFieldNamesMatchTheRules() {
        // firebase/firestore.rules spells these out; if one changes here it must change there.
        val rules = java.io.File("../firebase/firestore.rules").readText()
        for (name in listOf(
            Fields.DISPLAY_NAME, Fields.PUBLIC_KEY, Fields.OWNER_UID, Fields.KEY_VERSION, Fields.REV, Fields.SEALED_PAYLOAD,
            Fields.MEMBER_COUNT, Fields.CREATED_AT, Fields.ROLE, Fields.JOINED_AT, Fields.DONE_AT, Fields.WRAPPED_KEY,
            Fields.WRAPPED_KEY_VERSION, Fields.LAST_MESSAGE_AT, Fields.INVITE_ID, Fields.SENDER_UID, Fields.KIND,
            Fields.SEALED_BODY, Fields.ROOM_ID, Fields.CREATED_BY, Fields.EXPIRES_AT, Fields.REVOKED, Fields.SEALED_KEY,
        )) assertTrue("the rules never mention '$name'", rules.contains("'$name'") || rules.contains(".$name"))
    }
}
