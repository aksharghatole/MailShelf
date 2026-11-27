package com.mailshelf.app

import org.junit.Assert.*
import org.junit.Test

class InboxHistoryTest {
    private fun mail(uid: Long, time: Long = uid, email: String = "a@example.com") =
        MailItem(email, uid, 1, "Sender", "Subject", time, true)

    @Test fun oldestUsesUidNotDateAndOnlySelectedAccount() {
        val older = mail(10, 900)
        assertEquals(older, InboxLogic.oldest(listOf(mail(20, 1), older, mail(1, email = "b@example.com")), older.email))
        assertNull(InboxLogic.oldest(emptyList(), older.email))
    }

    @Test fun mergeDeduplicatesAndPreservesOtherAccounts() {
        val current = mail(20)
        val other = mail(20, email = "b@example.com")
        val old = mail(10)
        assertEquals(listOf(current, other, old), InboxLogic.merge(listOf(current, other), listOf(old, current.copy(unread = false))))
    }

    @Test fun historyCapacityIsBoundedPerAccount() {
        val email = "a@example.com"
        assertEquals(20, InboxLogic.remainingCapacity(emptyList(), email))
        val loaded = (1L..195L).map { mail(it) }
        assertEquals(5, InboxLogic.remainingCapacity(loaded, email))
        assertEquals(20, InboxLogic.remainingCapacity(loaded, "b@example.com"))
        assertEquals(0, InboxLogic.remainingCapacity(loaded + (196L..200L).map { mail(it) }, email))
    }

    @Test fun olderSequenceRangeStopsAtStart() {
        assertTrue(InboxLogic.recentRange(0).isEmpty())
        assertEquals(1..5, InboxLogic.recentRange(5))
        assertEquals(60..79, InboxLogic.recentRange(79))
    }
}
