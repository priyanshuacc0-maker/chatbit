package com.meshline.chat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.meshline.chat.security.AtRestCrypto

data class StoredMessage(
    val id: String,
    val peerId: String,
    val senderId: String,
    val body: String,
    val sentAt: Long,
    val status: String,
    val outgoing: Boolean,
)

data class ConversationSummary(
    val peerId: String,
    val displayName: String,
    val lastMessage: String,
    val updatedAt: Long,
)

class ChatDatabase(context: Context) : SQLiteOpenHelper(context, "meshline.db", null, 1) {
    private val atRestCrypto = AtRestCrypto()
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE peers (
            peer_id TEXT PRIMARY KEY NOT NULL,
            display_name TEXT NOT NULL,
            last_seen INTEGER NOT NULL
        )""")
        db.execSQL("""CREATE TABLE messages (
            id TEXT PRIMARY KEY NOT NULL,
            peer_id TEXT NOT NULL,
            sender_id TEXT NOT NULL,
            body TEXT NOT NULL,
            sent_at INTEGER NOT NULL,
            status TEXT NOT NULL,
            outgoing INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("CREATE INDEX idx_messages_peer_time ON messages(peer_id, sent_at)")
        db.execSQL("CREATE INDEX idx_messages_delivery ON messages(peer_id, status, outgoing)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun upsertPeer(peerId: String, name: String, now: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put("peer_id", peerId)
            put("display_name", name.take(64).ifBlank { "Nearby device" })
            put("last_seen", now)
        }
        writableDatabase.insertWithOnConflict("peers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun insertMessage(message: StoredMessage): Boolean {
        val values = ContentValues().apply {
            put("id", message.id)
            put("peer_id", message.peerId)
            put("sender_id", message.senderId)
            put("body", atRestCrypto.encrypt(message.body))
            put("sent_at", message.sentAt)
            put("status", message.status)
            put("outgoing", if (message.outgoing) 1 else 0)
        }
        return writableDatabase.insertWithOnConflict(
            "messages", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    /** Finds an already-paired peer from the first 32 hex characters of its device ID. */
    @Synchronized
    fun peerIdForPrefix(prefix: String): String? {
        if (prefix.length != 32 || !prefix.all { it in '0'..'9' || it in 'a'..'f' }) return null
        readableDatabase.rawQuery(
            "SELECT peer_id FROM peers WHERE substr(peer_id, 1, 32) = ? LIMIT 1", arrayOf(prefix)
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    @Synchronized
    fun isOutgoingUndelivered(messageId: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM messages WHERE id=? AND outgoing=1 AND status IN ('PENDING','SENT') LIMIT 1",
            arrayOf(messageId)
        ).use { it.moveToFirst() }

    /** Removes the contact and its whole conversation (also revokes the pinned identity). */
    @Synchronized
    fun deletePeer(peerId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", "peer_id=?", arrayOf(peerId))
            db.delete("peers", "peer_id=?", arrayOf(peerId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun updateStatus(messageId: String, peerId: String, status: String) {
        val values = ContentValues().apply { put("status", status) }
        writableDatabase.update(
            "messages", values,
            "id=? AND peer_id=? AND outgoing=1 AND (status!='DELIVERED' OR ?='DELIVERED')",
            arrayOf(messageId, peerId, status)
        )
    }

    @Synchronized
    fun messages(peerId: String, limit: Int = 500): List<StoredMessage> {
        val result = mutableListOf<StoredMessage>()
        readableDatabase.query(
            "messages", arrayOf("id", "peer_id", "sender_id", "body", "sent_at", "status", "outgoing"),
            "peer_id=?", arrayOf(peerId), null, null, "sent_at ASC", limit.coerceIn(1, 1000).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) result += StoredMessage(
                id = cursor.getString(0), peerId = cursor.getString(1),
                senderId = cursor.getString(2), body = decryptOrPlaceholder(cursor.getString(3)),
                sentAt = cursor.getLong(4), status = cursor.getString(5),
                outgoing = cursor.getInt(6) == 1
            )
        }
        return result
    }

    @Synchronized
    fun undelivered(peerId: String): List<StoredMessage> {
        val result = mutableListOf<StoredMessage>()
        readableDatabase.query(
            "messages", arrayOf("id", "peer_id", "sender_id", "body", "sent_at", "status", "outgoing"),
            "peer_id=? AND outgoing=1 AND status IN ('PENDING','SENT')",
            arrayOf(peerId), null, null, "sent_at ASC", "200"
        ).use { cursor ->
            while (cursor.moveToNext()) result += StoredMessage(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                decryptOrPlaceholder(cursor.getString(3)), cursor.getLong(4), cursor.getString(5),
                cursor.getInt(6) == 1
            )
        }
        return result
    }

    @Synchronized
    fun conversations(): List<ConversationSummary> {
        val result = mutableListOf<ConversationSummary>()
        readableDatabase.rawQuery(
            """SELECT p.peer_id, p.display_name,
                COALESCE((SELECT body FROM messages m WHERE m.peer_id=p.peer_id ORDER BY m.sent_at DESC LIMIT 1), ''),
                COALESCE((SELECT sent_at FROM messages m WHERE m.peer_id=p.peer_id ORDER BY m.sent_at DESC LIMIT 1), p.last_seen)
                FROM peers p ORDER BY 4 DESC""", null
        ).use { cursor ->
            while (cursor.moveToNext()) result += ConversationSummary(
                cursor.getString(0), cursor.getString(1), if (cursor.getString(2).isBlank()) "" else decryptOrPlaceholder(cursor.getString(2)), cursor.getLong(3)
            )
        }
        return result
    }

    private fun decryptOrPlaceholder(value: String): String = try {
        atRestCrypto.decrypt(value)
    } catch (_: Exception) {
        "[This message cannot be decrypted on this device]"
    }

}
