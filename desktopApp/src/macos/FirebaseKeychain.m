#import <Foundation/Foundation.h>
#import <Security/Security.h>
#include <jni.h>
#include <stdio.h>

#define JNI_METHOD(name) Java_com_theunderseer_simplio_auth_MacFirebaseSessionStore_##name

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
    jclass exception = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception == NULL) return;
    char message[64];
    snprintf(message, sizeof(message), "Keychain OSStatus %d", (int)status);
    (*env)->ThrowNew(env, exception, message);
}

JNIEXPORT jbyteArray JNICALL JNI_METHOD(readItem)(JNIEnv *env, jobject self, jstring projectId) {
    @autoreleasepool {
        NSMutableDictionary *query = [itemQuery(env, projectId) mutableCopy];
        if (query == nil) return NULL;
        query[(__bridge NSString *)kSecReturnData] = @YES;
        CFTypeRef result = NULL;
        OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)query, &result);
        if (status == errSecItemNotFound) return NULL;
        if (status != errSecSuccess) {
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

JNIEXPORT void JNICALL JNI_METHOD(writeItem)(JNIEnv *env, jobject self, jstring projectId, jbyteArray bytes) {
    @autoreleasepool {
        NSDictionary *query = itemQuery(env, projectId);
        if (query == nil) return;
        jsize size = (*env)->GetArrayLength(env, bytes);
        NSMutableData *data = [NSMutableData dataWithLength:(NSUInteger)size];
        (*env)->GetByteArrayRegion(env, bytes, 0, size, data.mutableBytes);
        if ((*env)->ExceptionCheck(env)) return;
        NSDictionary *attributes = @{ (__bridge NSString *)kSecValueData: data };
        OSStatus status = SecItemUpdate((__bridge CFDictionaryRef)query, (__bridge CFDictionaryRef)attributes);
        if (status == errSecItemNotFound) {
            NSMutableDictionary *item = [query mutableCopy];
            [item addEntriesFromDictionary:attributes];
            status = SecItemAdd((__bridge CFDictionaryRef)item, NULL);
            if (status == errSecDuplicateItem) {
                status = SecItemUpdate((__bridge CFDictionaryRef)query, (__bridge CFDictionaryRef)attributes);
            }
        }
        if (status != errSecSuccess) throwStatus(env, status);
    }
}

JNIEXPORT void JNICALL JNI_METHOD(clearItem)(JNIEnv *env, jobject self, jstring projectId) {
    @autoreleasepool {
        NSDictionary *query = itemQuery(env, projectId);
        if (query == nil) return;
        OSStatus status = SecItemDelete((__bridge CFDictionaryRef)query);
        if (status != errSecSuccess && status != errSecItemNotFound) throwStatus(env, status);
    }
}
