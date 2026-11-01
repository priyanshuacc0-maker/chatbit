package com.meshline.chat

import android.app.Application
import com.meshline.chat.network.ChatRepository

/**
 * The repository lives for the whole process (not the Activity), so connections and the retry
 * queue keep working while the app is in the background under [NearbyService].
 */
class MeshlineApplication : Application() {
    val repository: ChatRepository by lazy { ChatRepository(this) }
}
