package com.hsib.sib.core.social

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class SocialCryptoTest {
    private val key = RoomKey.generate()
    private fun ctx(room: String = "room1", purpose: Purpose = Purpose.MESSAGE, version: Int = 1, item: String = "m1", sender: String = "uidA") =
        SealContext(room, purpose, version, item, sender)

    private fun opened(r: Opened<String>): String = (r as Opened.Ok).value
    private fun errorOf(r: Opened<*>): OpenError = (r as Opened.Err).error

    // ---- sealed values -----------------------------------------------------------------------

    @Test
    fun aSealedMessageOpensToTheSameText() {
        for (text in listOf("hello", "", "naïve café", "日本語のテキスト", "emoji 👍🏽 and more", "line one\nline two")) {
            val sealed = SocialCrypto.sealText(key, ctx(), text)
            assertEquals(text, opened(SocialCrypto.openText(key, ctx(), sealed)))
        }
    }

    @Test
    fun sealingTheSameTextTwiceGivesDifferentValues() {
        assertNotEquals(SocialCrypto.sealText(key, ctx(), "same"), SocialCrypto.sealText(key, ctx(), "same"))
    }

    @Test
    fun theStoredSizeHidesTheExactLength() {
        val short = SocialCrypto.sealText(key, ctx(), "a").length
        val medium = SocialCrypto.sealText(key, ctx(), "a".repeat(40)).length
        val long = SocialCrypto.sealText(key, ctx(), "a".repeat(400)).length
        assertEquals(short, medium)
        assertTrue(long > medium)
    }

    @Test
    fun aWrongKeyDoesNotOpenIt() {
        val sealed = SocialCrypto.sealText(key, ctx(), "secret")
        assertEquals(OpenError.AUTH_FAILED, errorOf(SocialCrypto.openText(RoomKey.generate(), ctx(), sealed)))
    }

    @Test
    fun aValueCannotBeMovedToAnotherRoomFieldMessageOrSender() {
        val sealed = SocialCrypto.sealText(key, ctx(), "secret")
        assertEquals(OpenError.AUTH_FAILED, errorOf(SocialCrypto.openText(key, ctx(room = "room2"), sealed)))
        assertEquals(OpenError.AUTH_FAILED, errorOf(SocialCrypto.openText(key, ctx(purpose = Purpose.PAYLOAD), sealed)))
        assertEquals(OpenError.AUTH_FAILED, errorOf(SocialCrypto.openText(key, ctx(item = "m2"), sealed)))
        assertEquals(OpenError.AUTH_FAILED, errorOf(SocialCrypto.openText(key, ctx(sender = "uidB"), sealed)))
    }

    @Test
    fun theKeyVersionIsReadableAndChecked() {
        val sealed = SocialCrypto.sealText(key, ctx(version = 3), "x")
        assertEquals(3, SocialCrypto.keyVersionOf(sealed))
        assertEquals(OpenError.WRONG_KEY_VERSION, errorOf(SocialCrypto.openText(key, ctx(version = 2), sealed)))
        assertNull(SocialCrypto.keyVersionOf("not base64 !!"))
        assertNull(SocialCrypto.keyVersionOf(""))
    }

    @Test
    fun changingAnySingleBitFailsToOpen() {
        val raw = Base64.getUrlDecoder().decode(SocialCrypto.sealText(key, ctx(), "tamper with me"))
        for (i in raw.indices) {
            val copy = raw.copyOf()
            copy[i] = (copy[i].toInt() xor 0x01).toByte()
            val text = Base64.getUrlEncoder().withoutPadding().encodeToString(copy)
            assertTrue("byte $i was accepted", SocialCrypto.openText(key, ctx(), text) is Opened.Err)
        }
    }

    @Test
    fun truncatedOrExtendedValuesFailToOpen() {
        val raw = Base64.getUrlDecoder().decode(SocialCrypto.sealText(key, ctx(), "tamper with me"))
        val enc = Base64.getUrlEncoder().withoutPadding()
        for (n in listOf(0, 1, 4, 5, 20, raw.size - 1)) {
            assertTrue("prefix of $n bytes was accepted", SocialCrypto.openText(key, ctx(), enc.encodeToString(raw.copyOf(n))) is Opened.Err)
        }
        assertTrue(SocialCrypto.openText(key, ctx(), enc.encodeToString(raw + byteArrayOf(0))) is Opened.Err)
    }

    @Test
    fun garbageAndOversizedInputIsRefusedWithoutACrash() {
        for (s in listOf("", "!!!", "AAAA", "A".repeat(100), "A".repeat(SocialLimits.MAX_ENVELOPE_CHARS + 1), "%%%%%%%%")) {
            assertTrue("accepted: ${s.take(12)}", SocialCrypto.openText(key, ctx(), s) is Opened.Err)
        }
        assertEquals(OpenError.TOO_LARGE, errorOf(SocialCrypto.openText(key, ctx(), "A".repeat(SocialLimits.MAX_ENVELOPE_CHARS + 1))))
    }

    @Test
    fun anOversizedPlaintextIsNotSealed() {
        try {
            SocialCrypto.seal(key, ctx(), ByteArray(SocialLimits.MAX_SEALED_BYTES + 1))
            fail("should refuse")
        } catch (_: IllegalArgumentException) {
        }
        assertNotNull(SocialCrypto.seal(key, ctx(), ByteArray(SocialLimits.MAX_SEALED_BYTES)))
    }

    // ---- wrapping the room key for a person -----------------------------------------------------

    @Test
    fun aWrappedRoomKeyOpensOnlyOnTheRightPhone() {
        val bob = DeviceKeyPair.generate()
        val eve = DeviceKeyPair.generate()
        val wrapped = KeyWrap.wrap(key, bob.publicKey, "room1", 2, "bob")!!
        val got = (KeyWrap.unwrap(wrapped, bob, "room1", 2, "bob") as Opened.Ok).value
        assertArrayEquals(key.bytes, got.bytes)
        assertEquals(OpenError.AUTH_FAILED, errorOf(KeyWrap.unwrap(wrapped, eve, "room1", 2, "bob")))
    }

    @Test
    fun aWrappedKeyIsBoundToItsRoomVersionAndPerson() {
        val bob = DeviceKeyPair.generate()
        val wrapped = KeyWrap.wrap(key, bob.publicKey, "room1", 2, "bob")!!
        assertTrue(KeyWrap.unwrap(wrapped, bob, "room2", 2, "bob") is Opened.Err)
        assertTrue(KeyWrap.unwrap(wrapped, bob, "room1", 3, "bob") is Opened.Err)
        assertTrue(KeyWrap.unwrap(wrapped, bob, "room1", 2, "carol") is Opened.Err)
    }

    @Test
    fun changingAnyByteOfAWrappedKeyFailsToOpen() {
        val bob = DeviceKeyPair.generate()
        val raw = Base64.getUrlDecoder().decode(KeyWrap.wrap(key, bob.publicKey, "room1", 1, "bob")!!)
        for (i in raw.indices) {
            val copy = raw.copyOf()
            copy[i] = (copy[i].toInt() xor 0x80).toByte()
            val text = Base64.getUrlEncoder().withoutPadding().encodeToString(copy)
            assertTrue("byte $i was accepted", KeyWrap.unwrap(text, bob, "room1", 1, "bob") is Opened.Err)
        }
    }

    @Test
    fun unusableRecipientKeysAreRefused() {
        assertNull(KeyWrap.wrap(key, ByteArray(31), "room1", 1, "x"))
        assertNull(KeyWrap.wrap(key, ByteArray(32), "room1", 1, "x"))
        val one = ByteArray(32).also { it[0] = 1 }
        assertNull(KeyWrap.wrap(key, one, "room1", 1, "x"))
        val good = DeviceKeyPair.generate()
        val map = KeyWrap.wrapForAll(key, mapOf("good" to good.publicKey, "bad" to ByteArray(32), "short" to ByteArray(5)), "room1", 1)
        assertEquals(setOf("good"), map.keys)
    }

    @Test
    fun aDeviceKeyRoundTripsThroughStorageAndRejectsDamage() {
        val pair = DeviceKeyPair.generate()
        val back = DeviceKeyPair.decode(pair.encode())!!
        assertArrayEquals(pair.privateKey, back.privateKey)
        assertArrayEquals(pair.publicKey, back.publicKey)
        val other = DeviceKeyPair.generate()
        assertNull(DeviceKeyPair.decode(pair.encode().substringBefore('.') + "." + other.publicText()))
        for (s in listOf("", ".", "a.b", "no dot", pair.encode() + ".x")) assertNull(DeviceKeyPair.decode(s))
        assertNull(DeviceKeyPair.publicFromText("short"))
        assertNotNull(DeviceKeyPair.publicFromText(pair.publicText()))
    }

    // ---- invites -----------------------------------------------------------------------------------

    @Test
    fun anInviteCodeHasTheExpectedShapeAndParsesBack() {
        val code = InviteCode.generate()
        val text = code.text()
        assertTrue(text.startsWith("SIB-"))
        assertEquals(8, text.removePrefix("SIB-").split("-").size)
        assertTrue(text.removePrefix("SIB-").split("-").all { it.length == 4 })
        val back = InviteCode.parse(text)!!
        assertEquals(code.id, back.id)
        assertEquals(code.secret, back.secret)
        assertEquals(InviteCode.ID_CHARS, code.id.length)
        assertEquals(InviteCode.SECRET_CHARS, code.secret.length)
        assertTrue((code.id + code.secret).all { Crockford.ALPHABET.indexOf(it) >= 0 })
        assertNotEquals(code.text(), InviteCode.generate().text())
    }

    @Test
    fun aCodeIsReadForgivingly() {
        val code = InviteCode.generate()
        val plain = code.text().removePrefix("SIB-")
        for (variant in listOf(code.text().lowercase(), plain, plain.replace("-", ""), plain.replace("-", " "), "  ${code.text()}\n", "SIB " + plain, "sib" + plain.replace("-", ""))) {
            assertEquals("variant: $variant", code.id, InviteCode.parse(variant)?.id)
        }
        val confusable = code.text().replace('0', 'O').replace('1', 'I')
        assertEquals(code.id, InviteCode.parse(confusable)?.id)
    }

    @Test
    fun mistypedOrBrokenCodesAreRejected() {
        val code = InviteCode.generate()
        for (s in listOf("", "SIB-", "hello", code.text().dropLast(1), code.text() + "A", "SIB-!!!!-????", "x".repeat(500), code.text().replace('-', '/'))) {
            assertNull("accepted: ${s.take(20)}", InviteCode.parse(s))
        }
        val body = code.id + code.secret + code.text().filter { it != '-' }.takeLast(2)
        var missed = 0
        var tried = 0
        for (i in 0 until InviteCode.ID_CHARS + InviteCode.SECRET_CHARS) {
            for (c in Crockford.ALPHABET) {
                if (c == body[i]) continue
                tried++
                if (InviteCode.parse(body.substring(0, i) + c + body.substring(i + 1)) != null) missed++
            }
        }
        // A 10 bit checksum misses about one single-character typo in a thousand.
        assertTrue("missed $missed of $tried typos", missed * 100 <= tried)
        // Any change to the checksum itself is always caught.
        for (c in Crockford.ALPHABET) if (c != body.last()) assertNull(InviteCode.parse(body.dropLast(1) + c))
    }

    @Test
    fun theInviteKeyDependsOnTheWholeCode() {
        val a = InviteCode.generate()
        val b = InviteCode.generate()
        assertEquals(32, a.key().size)
        assertArrayEquals(a.key(), InviteCode.parse(a.text())!!.key())
        assertFalse(a.key().contentEquals(b.key()))
    }

    @Test
    fun aRoomKeyTravelsInsideAnInviteAndOnlyTheCodeOpensIt() {
        val code = InviteCode.generate()
        val sealed = InviteSeal.seal(key, code, "room1", 4)
        val (got, version) = (InviteSeal.open(sealed, InviteCode.parse(code.text())!!, "room1") as Opened.Ok).value
        assertArrayEquals(key.bytes, got.bytes)
        assertEquals(4, version)
        assertTrue(InviteSeal.open(sealed, InviteCode.generate(), "room1") is Opened.Err)
        assertTrue(InviteSeal.open(sealed, code, "room2") is Opened.Err)
    }

    @Test
    fun aTamperedInviteSealFailsToOpen() {
        val code = InviteCode.generate()
        val raw = Base64.getUrlDecoder().decode(InviteSeal.seal(key, code, "room1", 1))
        for (i in raw.indices) {
            val copy = raw.copyOf()
            copy[i] = (copy[i].toInt() xor 0x04).toByte()
            val text = Base64.getUrlEncoder().withoutPadding().encodeToString(copy)
            assertTrue("byte $i was accepted", InviteSeal.open(text, code, "room1") is Opened.Err)
        }
        for (s in listOf("", "AAAA", "A".repeat(600))) assertTrue(InviteSeal.open(s, code, "room1") is Opened.Err)
    }

    // ---- two phones, end to end ----------------------------------------------------------------

    @Test
    fun anOwnerInvitesAFriendWhoThenChatsAndTheOwnerRemovesThem() {
        val room = "roomX"
        val ownerPhone = DeviceKeyPair.generate()
        val friendPhone = DeviceKeyPair.generate()
        val thirdPhone = DeviceKeyPair.generate()

        // The owner makes the room and seals the deadline details.
        val keyV1 = RoomKey.generate()
        val payload = SocialCrypto.seal(keyV1, SealContext(room, Purpose.PAYLOAD, 1), "details".toByteArray())

        // The owner makes an invite. The server stores only the id, the sealed key and the expiry.
        val code = InviteCode.generate()
        val serverInvite = InviteSeal.seal(keyV1, code, room, 1)

        // The friend types the code, opens the key, and can read the details and write a message.
        val (friendKey, v) = (InviteSeal.open(serverInvite, InviteCode.parse(code.text())!!, room) as Opened.Ok).value
        assertEquals("details", String((SocialCrypto.open(friendKey, SealContext(room, Purpose.PAYLOAD, v), payload) as Opened.Ok).value))
        val msg = SocialCrypto.sealText(friendKey, SealContext(room, Purpose.MESSAGE, v, "m1", "friend"), "I can do Thursday")
        assertEquals("I can do Thursday", opened(SocialCrypto.openText(keyV1, SealContext(room, Purpose.MESSAGE, 1, "m1", "friend"), msg)))

        // A third person joins too, and the owner wraps the key for each person's phone.
        val wraps1 = KeyWrap.wrapForAll(keyV1, mapOf("owner" to ownerPhone.publicKey, "friend" to friendPhone.publicKey, "third" to thirdPhone.publicKey), room, 1)
        assertEquals(3, wraps1.size)

        // The owner removes the friend: a new key is made and wrapped only for the people who stay.
        val keyV2 = RoomKey.generate()
        val wraps2 = KeyWrap.wrapForAll(keyV2, mapOf("owner" to ownerPhone.publicKey, "third" to thirdPhone.publicKey), room, 2)
        assertEquals(setOf("owner", "third"), wraps2.keys)
        val thirdKey = (KeyWrap.unwrap(wraps2.getValue("third"), thirdPhone, room, 2, "third") as Opened.Ok).value
        val later = SocialCrypto.sealText(keyV2, SealContext(room, Purpose.MESSAGE, 2, "m2", "owner"), "after the change")
        assertEquals("after the change", opened(SocialCrypto.openText(thirdKey, SealContext(room, Purpose.MESSAGE, 2, "m2", "owner"), later)))

        // The removed friend still holds the old key. It cannot open anything made with the new one, even when forced.
        assertEquals(OpenError.WRONG_KEY_VERSION, errorOf(SocialCrypto.openText(friendKey, SealContext(room, Purpose.MESSAGE, 1, "m2", "owner"), later)))
        assertEquals(OpenError.AUTH_FAILED, errorOf(SocialCrypto.openText(friendKey, SealContext(room, Purpose.MESSAGE, 2, "m2", "owner"), later)))
        // And the friend's key for them was never wrapped again, so their phone has nothing for version 2.
        assertFalse(wraps2.containsKey("friend"))
    }
}
