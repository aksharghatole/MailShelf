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

    const val PAGE_SIZE = 20
    const val MAX_LOADED_PER_ACCOUNT = 200

    fun oldest(messages: List<MailItem>, account: String): MailItem? =
        messages.filter { it.email == account }.minByOrNull { it.uid }

    fun remainingCapacity(messages: List<MailItem>, account: String): Int =
        (MAX_LOADED_PER_ACCOUNT - messages.count { it.email == account }).coerceIn(0, PAGE_SIZE)

    fun merge(existing: List<MailItem>, older: List<MailItem>): List<MailItem> =
        (existing + older).distinctBy { it.key }.sortedByDescending { it.time }

    /** IMAP message sequence numbers are one-based. */
    fun recentRange(count: Int, limit: Int = PAGE_SIZE): IntRange {
        require(count >= 0)
        require(limit > 0)
        return if (count == 0) IntRange.EMPTY else maxOf(1, count - limit + 1)..count
    }
}
