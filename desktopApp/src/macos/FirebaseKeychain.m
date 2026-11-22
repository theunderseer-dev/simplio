#import <Foundation/Foundation.h>
#import <Security/Security.h>
#include <jni.h>
#include <pthread.h>

#define JNI_METHOD(name) Java_com_theunderseer_simplio_auth_MacFirebaseSessionStore_##name

// Existing sessions use the file-based Keychain. Serialize calls while its process-wide UI setting is changed.
static pthread_mutex_t interactionLock = PTHREAD_MUTEX_INITIALIZER;

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
static OSStatus withKeychainInteraction(BOOL allowUserInteraction, OSStatus (^operation)(void)) {
    pthread_mutex_lock(&interactionLock);
    OSStatus status;
    if (allowUserInteraction) {
        status = operation();
    } else {
        Boolean previous;
        status = SecKeychainGetUserInteractionAllowed(&previous);
        if (status == errSecSuccess) {
            status = SecKeychainSetUserInteractionAllowed(false);
            if (status == errSecSuccess) {
                status = operation();
                OSStatus restoreStatus = SecKeychainSetUserInteractionAllowed(previous);
                if (status == errSecSuccess) status = restoreStatus;
            }
        }
    }
    pthread_mutex_unlock(&interactionLock);
    return status;
}
#pragma clang diagnostic pop

static NSDictionary *itemQuery(JNIEnv *env, jstring projectId) {
    const char *raw = (*env)->GetStringUTFChars(env, projectId, NULL);
    if (raw == NULL) return nil;
    NSString *account = [NSString stringWithUTF8String:raw];
    (*env)->ReleaseStringUTFChars(env, projectId, raw);
    if (account == nil) return nil;
    return @{
        (__bridge NSString *)kSecClass: (__bridge NSString *)kSecClassGenericPassword,
        (__bridge NSString *)kSecAttrService: @"com.theunderseer.simplio.firebase-session",
        (__bridge NSString *)kSecAttrAccount: account,
        (__bridge NSString *)kSecAttrSynchronizable: @NO,
    };
}

static void throwStatus(JNIEnv *env, OSStatus status) {
    jclass exceptionClass = (*env)->FindClass(env, "com/theunderseer/simplio/auth/MacKeychainOperationException");
    if (exceptionClass == NULL) return;
    jmethodID constructor = (*env)->GetMethodID(env, exceptionClass, "<init>", "(IZ)V");
    if (constructor == NULL) return;
    jboolean temporarilyUnavailable = status == errSecInteractionNotAllowed ||
        status == errSecInteractionRequired || status == errSecNotAvailable || status == errSecInDarkWake;
    jthrowable exception = (*env)->NewObject(env, exceptionClass, constructor, (jint)status, temporarilyUnavailable);
    if (exception != NULL) (*env)->Throw(env, exception);
}

JNIEXPORT jbyteArray JNICALL JNI_METHOD(readItemWithInteraction)(
    JNIEnv *env, jobject self, jstring projectId, jboolean allowUserInteraction
) {
    @autoreleasepool {
        NSMutableDictionary *query = [itemQuery(env, projectId) mutableCopy];
        if (query == nil) return NULL;
        query[(__bridge NSString *)kSecReturnData] = @YES;
        __block CFTypeRef result = NULL;
        OSStatus status = withKeychainInteraction(allowUserInteraction, ^OSStatus {
            return SecItemCopyMatching((__bridge CFDictionaryRef)query, &result);
        });
        if (status == errSecItemNotFound) return NULL;
        if (status != errSecSuccess) {
            if (result != NULL) CFRelease(result);
            throwStatus(env, status);
            return NULL;
        }
        NSData *data = CFBridgingRelease(result);
        jbyteArray bytes = (*env)->NewByteArray(env, (jsize)data.length);
        if (bytes != NULL && data.length > 0) {
            (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)data.length, data.bytes);
        }
        return bytes;
    }
}

JNIEXPORT void JNICALL JNI_METHOD(writeItem)(
    JNIEnv *env, jobject self, jstring projectId, jbyteArray bytes, jboolean allowUserInteraction
) {
    @autoreleasepool {
        NSDictionary *query = itemQuery(env, projectId);
        if (query == nil) return;
        jsize size = (*env)->GetArrayLength(env, bytes);
        NSMutableData *data = [NSMutableData dataWithLength:(NSUInteger)size];
        (*env)->GetByteArrayRegion(env, bytes, 0, size, data.mutableBytes);
        if ((*env)->ExceptionCheck(env)) return;
        NSDictionary *attributes = @{ (__bridge NSString *)kSecValueData: data };
        OSStatus status = withKeychainInteraction(allowUserInteraction, ^OSStatus {
            CFDictionaryRef keychainQuery = (__bridge CFDictionaryRef)query;
            CFDictionaryRef updates = (__bridge CFDictionaryRef)attributes;
            OSStatus writeStatus = SecItemUpdate(keychainQuery, updates);
            if (writeStatus == errSecItemNotFound) {
                NSMutableDictionary *item = [query mutableCopy];
                [item addEntriesFromDictionary:attributes];
                writeStatus = SecItemAdd((__bridge CFDictionaryRef)item, NULL);
                if (writeStatus == errSecDuplicateItem) writeStatus = SecItemUpdate(keychainQuery, updates);
            }
            return writeStatus;
        });
        if (status != errSecSuccess) throwStatus(env, status);
    }
}

JNIEXPORT void JNICALL JNI_METHOD(clearItem)(JNIEnv *env, jobject self, jstring projectId, jboolean allowUserInteraction) {
    @autoreleasepool {
        NSDictionary *query = itemQuery(env, projectId);
        if (query == nil) return;
        OSStatus status = withKeychainInteraction(allowUserInteraction, ^OSStatus {
            return SecItemDelete((__bridge CFDictionaryRef)query);
        });
        if (status != errSecSuccess && status != errSecItemNotFound) throwStatus(env, status);
    }
}
