package com.mailshelf.app

/** Pure inbox operations: no credentials, Android APIs or network access. */
internal object InboxLogic {
    fun filter(messages: List<MailItem>, account: String?, query: String): List<MailItem> {
        val needle = query.trim()
        return messages.filter { item ->
            (account == null || item.email == account) &&
                (item.sender.contains(needle, ignoreCase = true) ||
                    item.subject.contains(needle, ignoreCase = true) ||
                    item.email.contains(needle, ignoreCase = true))
        }
    }

    /** IMAP message sequence numbers are one-based. */
    fun recentRange(count: Int, limit: Int = 20): IntRange {
        require(count >= 0)
        require(limit > 0)
        return if (count == 0) IntRange.EMPTY else maxOf(1, count - limit + 1)..count
    }
}
