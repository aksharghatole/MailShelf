package com.mailshelf.app

import com.sun.mail.imap.IMAPFolder
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress

data class MailItem(val email: String, val uid: Long, val validity: Long, val sender: String, val subject: String, val time: Long, val unread: Boolean) {
    val key get() = "$email:$validity:$uid"
}

object Gmail {
    private fun <T> inbox(email: String, password: String, action: (IMAPFolder) -> T): T {
        val props = Properties().apply {
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "20000")
            put("mail.imaps.writetimeout", "20000")
            put("mail.imaps.peek", "true")
            put("mail.imaps.partialfetch", "true")
        }
        val store = Session.getInstance(props).getStore("imaps")
        try {
            store.connect("imap.gmail.com", 993, email, password)
            val folder = store.getFolder("INBOX") as IMAPFolder
            try {
                folder.open(Folder.READ_ONLY)
                return action(folder)
            } finally { if (folder.isOpen) folder.close(false) }
        } finally { if (store.isConnected) store.close() }
    }
    fun verify(email: String, password: String) = inbox(email, password) { Unit }
    fun recent(email: String, password: String): List<MailItem> = inbox(email, password) { f ->
        fetchRange(f, email, InboxLogic.recentRange(f.messageCount))
    }

    /** Resolve a UID to its current sequence number; never persist sequence numbers. */
    fun older(anchor: MailItem, password: String, limit: Int): List<MailItem> =
        inbox(anchor.email, password) { f ->
            require(limit in 1..InboxLogic.PAGE_SIZE)
            check(f.uidValidity == anchor.validity) { "Inbox changed; refresh first." }
            val boundary = f.getMessageByUID(anchor.uid) ?: error("Inbox changed; refresh first.")
            val number = boundary.messageNumber
            check(number > 0 && !boundary.isExpunged) { "Inbox changed; refresh first." }
            val page = fetchRange(f, anchor.email, InboxLogic.recentRange(number - 1, limit))
            check(!boundary.isExpunged && boundary.messageNumber == number &&
                page.all { it.uid < anchor.uid && it.validity == anchor.validity }) {
                "Inbox changed; refresh first."
            }
            page
        }

    private fun fetchRange(f: IMAPFolder, email: String, range: IntRange): List<MailItem> {
        if (range.isEmpty()) return emptyList()
        val msgs = f.getMessages(range.first, range.last)
        val profile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(UIDFolder.FetchProfileItem.UID)
        }
        f.fetch(msgs, profile)
        val validity = f.uidValidity
        return msgs.map { m ->
            val from = m.from?.firstOrNull()
            val uid = f.getUID(m)
            check(uid > 0 && !m.isExpunged) { "Inbox changed; refresh first." }
            MailItem(email, uid, validity,
                (from as? InternetAddress)?.toUnicodeString() ?: from?.toString() ?: "(Unknown sender)",
                m.subject ?: "(No subject)", m.receivedDate?.time ?: m.sentDate?.time ?: 0L,
                !m.isSet(Flags.Flag.SEEN))
        }
    }
    fun body(item: MailItem, password: String): String = inbox(item.email, password) { f ->
        check(f.uidValidity == item.validity) { "Inbox changed; refresh first." }
        val m = f.getMessageByUID(item.uid) ?: error("Message no longer available")
        plain(m, 0) ?: "No plain-text body available. HTML and attachments are not opened in this version."
    }
    private fun plain(part: Part, depth: Int): String? {
        if (depth > 8 || Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) || part.fileName != null) return null
        if (part.isMimeType("text/plain")) {
            val charset = runCatching { javax.mail.internet.ContentType(part.contentType).getParameter("charset") }.getOrNull()
            val encoding = runCatching { java.nio.charset.Charset.forName(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
            return part.inputStream.bufferedReader(encoding).use { reader ->
                val buffer = CharArray(8192)
                val out = StringBuilder()
                while (out.length < 100_000) {
                    val n = reader.read(buffer, 0, minOf(buffer.size, 100_000 - out.length))
                    if (n < 0) break
                    out.append(buffer, 0, n)
                }
                if (out.length == 100_000) out.append("\n[Text truncated]")
                out.toString()
            }
        }
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as? Multipart ?: return null
            for (i in 0 until minOf(multipart.count, 30)) {
                plain(multipart.getBodyPart(i), depth + 1)?.let { return it }
            }
        }
        return null
    }
}
