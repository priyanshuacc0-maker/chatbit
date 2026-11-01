package com.meshline.chat.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairInviteTest {
    private val inviter = "a".repeat(64)
    private val joiner = "b".repeat(64)

    @Test
    fun inviteSurvivesEncodeDecode() {
        val token = PairInvite.newToken()
        val uri = PairInvite.encode(PairInvite.Invite(inviter, token, "Sam & Co é"))
        val back = PairInvite.decode(uri)
        assertNotNull(back)
        assertEquals(inviter, back!!.deviceId)
        assertArrayEquals(token, back.token)
        assertEquals("Sam & Co é", back.name)
    }

    @Test
    fun malformedInvitesAreRejected() {
        assertNull(PairInvite.decode("hello"))
        assertNull(PairInvite.decode("meshline://pair?i=zz&t=AAAA"))
        assertNull(PairInvite.decode("meshline://pair?i=$inviter&t=AAAA"))
        assertNull(PairInvite.decode("https://example.com/?i=$inviter"))
    }

    @Test
    fun proofBindsTokenAndBothIds() {
        val token = PairInvite.newToken()
        val proof = PairInvite.proof(token, joiner, inviter)
        assertTrue(PairInvite.verifyProof(token, joiner, inviter, proof))
        assertFalse(PairInvite.verifyProof(PairInvite.newToken(), joiner, inviter, proof))
        assertFalse(PairInvite.verifyProof(token, "c".repeat(64), inviter, proof))
        assertFalse(PairInvite.verifyProof(token, joiner, "c".repeat(64), proof))
        assertFalse(PairInvite.verifyProof(token, joiner, inviter, ""))
    }

    @Test
    fun tokensAreUnique() {
        assertFalse(PairInvite.newToken().contentEquals(PairInvite.newToken()))
    }
}
