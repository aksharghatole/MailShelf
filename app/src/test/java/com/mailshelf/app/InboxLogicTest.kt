package com.mailshelf.app

import org.junit.Assert.*
import org.junit.Test

class InboxLogicTest {
    private val first = MailItem("one@example.com", 1, 10, "Alice", "Verification Code", 20, true)
    private val second = MailItem("two@example.com", 1, 10, "Bob", "Invoice", 10, false)
    private val messages = listOf(first, second)

    @Test fun blankSearchPreservesOrder() {
        assertEquals(messages, InboxLogic.filter(messages, null, "   "))
    }

    @Test fun searchMatchesSenderSubjectAndAddressIgnoringCase() {
        assertEquals(listOf(first), InboxLogic.filter(messages, null, "  ALICE "))
        assertEquals(listOf(first), InboxLogic.filter(messages, null, "code"))
        assertEquals(listOf(second), InboxLogic.filter(messages, null, "TWO@"))
    }

    @Test fun accountSelectionCombinesWithSearch() {
        assertEquals(listOf(second), InboxLogic.filter(messages, second.email, ""))
        assertTrue(InboxLogic.filter(messages, second.email, "Alice").isEmpty())
        assertTrue(InboxLogic.filter(messages, "missing@example.com", "").isEmpty())
    }

    @Test fun recentRangeHandlesEmptySmallAndLargeInboxes() {
        assertTrue(InboxLogic.recentRange(0).isEmpty())
        assertEquals(1..1, InboxLogic.recentRange(1))
        assertEquals(1..20, InboxLogic.recentRange(20))
        assertEquals(81..100, InboxLogic.recentRange(100))
        assertEquals(999981..1000000, InboxLogic.recentRange(1000000))
        assertEquals(8..10, InboxLogic.recentRange(10, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeMessageCount() {
        InboxLogic.recentRange(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPageSize() {
        InboxLogic.recentRange(10, 0)
    }

    @Test fun messageIdentityIncludesAccountAndMailboxValidity() {
        assertNotEquals(first.key, second.key)
        assertNotEquals(first.key, first.copy(validity = 11).key)
        assertNotEquals(first.key, first.copy(uid = 2).key)
        assertEquals(first.key, first.copy(unread = false).key)
    }
}
