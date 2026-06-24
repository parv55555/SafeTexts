package com.parv.safetexts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactUtils {
    fun isContactExists(context: Context, senderName: String): Boolean {
        if (senderName.isEmpty()) return false
        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, 
                Uri.encode(senderName)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup._ID)
            context.contentResolver.query(lookupUri, projection, null, null, null).use { cursor ->
                return cursor != null && cursor.count > 0
            }
        } catch (e: Exception) {
            return false
        }
    }
}
