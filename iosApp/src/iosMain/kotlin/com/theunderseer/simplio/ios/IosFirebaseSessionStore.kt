package com.theunderseer.simplio.ios

import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStorageException
import com.theunderseer.simplio.shared.firebase.auth.FirebaseSessionStore
import com.theunderseer.simplio.shared.firebase.auth.RememberedFirebaseSession
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
internal class IosFirebaseSessionStore(private val projectId: String) : FirebaseSessionStore {
    override suspend fun read(): RememberedFirebaseSession? = withContext(Dispatchers.IO) {
        val stored = readItem() ?: return@withContext null
        val parts = stored.split('\n', limit = 2)
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            deleteItem()
            return@withContext null
        }
        RememberedFirebaseSession(parts[0], parts[1])
    }

    // This item uses device unlock only and requires no Keychain interaction on any operation.
    override suspend fun write(session: RememberedFirebaseSession, allowUserInteraction: Boolean) {
        withContext(Dispatchers.IO) {
            require('\n' !in session.userId)
            val encoded = "${session.userId}\n${session.refreshToken}".encodeToByteArray()
            val data = encoded.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), encoded.size.toLong()) }
                ?: throw FirebaseSessionStorageException()
            try {
                withItemQuery { query ->
                    withDictionary { update ->
                        CFDictionarySetValue(update, kSecValueData, data)
                        var status = SecItemUpdate(query, update)
                        if (status == errSecItemNotFound) {
                            status = withItemQuery { item ->
                                CFDictionarySetValue(item, kSecValueData, data)
                                CFDictionarySetValue(
                                    item,
                                    kSecAttrAccessible,
                                    kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                                )
                                SecItemAdd(item, null)
                            }
                            if (status == errSecDuplicateItem) status = SecItemUpdate(query, update)
                        }
                        requireKeychainSuccess(status)
                    }
                }
            } finally {
                CFRelease(data)
            }
        }
    }

    override suspend fun clear(allowUserInteraction: Boolean) = withContext(Dispatchers.IO) {
        deleteItem()
    }

    private fun readItem(): String? = withItemQuery { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecItemNotFound) return@memScoped null
            requireKeychainSuccess(status)
            val data: CFDataRef = result.value?.reinterpret() ?: throw FirebaseSessionStorageException()
            try {
                CFDataGetBytePtr(data)?.readBytes(CFDataGetLength(data).toInt())?.decodeToString().orEmpty()
            } finally {
                CFRelease(data)
            }
        }
    }

    private fun deleteItem() = withItemQuery { query ->
        val status = SecItemDelete(query)
        if (status != errSecItemNotFound) requireKeychainSuccess(status)
    }

    private inline fun <T> withItemQuery(block: (CFMutableDictionaryRef) -> T): T = withDictionary { query ->
        val service = CFStringCreateWithCString(
            null,
            "com.theunderseer.simplio.firebase-session",
            kCFStringEncodingUTF8,
        ) ?: throw FirebaseSessionStorageException()
        try {
            val account = CFStringCreateWithCString(null, projectId, kCFStringEncodingUTF8)
                ?: throw FirebaseSessionStorageException()
            try {
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(query, kSecAttrService, service)
                CFDictionarySetValue(query, kSecAttrAccount, account)
                block(query)
            } finally {
                CFRelease(account)
            }
        } finally {
            CFRelease(service)
        }
    }

    private inline fun <T> withDictionary(block: (CFMutableDictionaryRef) -> T): T {
        val dictionary =
            CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
                ?: throw FirebaseSessionStorageException()
        return try {
            block(dictionary)
        } finally {
            CFRelease(dictionary)
        }
    }

    private fun requireKeychainSuccess(status: Int) {
        if (status != errSecSuccess) {
            throw FirebaseSessionStorageException(IllegalStateException("Keychain OSStatus $status"))
        }
    }
}
