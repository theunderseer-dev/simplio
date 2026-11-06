#import <AppKit/AppKit.h>
#import <UserNotifications/UserNotifications.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

static NSString *const ReminderPrefix = @"simplio.attendance.";
static NSString *const ReminderCategory = @"simplio.attendance";
static NSString *const RevisionKey = @"revision";
static JavaVM *jvm;
static jobject eventConsumer;
static jmethodID acceptEvent;
static BOOL diagnosticsEnabled;

static void WithEnv(void (^action)(JNIEnv *)) {
    JNIEnv *env = NULL;
    jint state = (*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_8);
    if (state == JNI_EDETACHED &&
        (*jvm)->AttachCurrentThreadAsDaemon(jvm, (void **)&env, NULL) != JNI_OK) return;
    if (env == NULL) return;
    @autoreleasepool { action(env); }
    if (state == JNI_EDETACHED) (*jvm)->DetachCurrentThread(jvm);
}

static NSString *FromJava(JNIEnv *env, jstring value) {
    const jchar *chars = (*env)->GetStringChars(env, value, NULL);
    if (chars == NULL) return @"";
    NSString *result = [NSString stringWithCharacters:chars length:(NSUInteger)(*env)->GetStringLength(env, value)];
    (*env)->ReleaseStringChars(env, value, chars);
    return result;
}

static jstring ToJava(JNIEnv *env, NSString *value) {
    NSUInteger length = value.length;
    if (length == 0) return (*env)->NewStringUTF(env, "");
    jchar *chars = calloc(length, sizeof(jchar));
    if (chars == NULL) return NULL;
    [value getCharacters:chars range:NSMakeRange(0, length)];
    jstring result = (*env)->NewString(env, chars, (jsize)length);
    free(chars);
    return result;
}

static void SendEvent(NSString *kind, NSString *identifier) {
    WithEnv(^(JNIEnv *env) {
        jstring javaKind = ToJava(env, kind);
        jstring javaId = ToJava(env, identifier);
        (*env)->CallVoidMethod(env, eventConsumer, acceptEvent, javaKind, javaId);
        (*env)->DeleteLocalRef(env, javaId);
        (*env)->DeleteLocalRef(env, javaKind);
    });
}

static void Complete(jobject callback, NSString *value, NSArray<NSString *> *values) {
    WithEnv(^(JNIEnv *env) {
        jclass type = (*env)->FindClass(env, "java/util/function/Consumer");
        jmethodID accept = (*env)->GetMethodID(env, type, "accept", "(Ljava/lang/Object;)V");
        jobject result;
        if (values == nil) {
            result = ToJava(env, value);
        } else {
            jclass strings = (*env)->FindClass(env, "java/lang/String");
            jobjectArray array = (*env)->NewObjectArray(env, (jsize)values.count, strings, NULL);
            for (NSUInteger i = 0; i < values.count; i++) {
                jstring item = ToJava(env, values[i]);
                (*env)->SetObjectArrayElement(env, array, (jsize)i, item);
                (*env)->DeleteLocalRef(env, item);
            }
            (*env)->DeleteLocalRef(env, strings);
            result = array;
        }
        (*env)->CallVoidMethod(env, callback, accept, result);
        (*env)->DeleteLocalRef(env, result);
        (*env)->DeleteLocalRef(env, type);
        (*env)->DeleteGlobalRef(env, callback);
    });
}

static NSArray<NSString *> *FromJavaArray(JNIEnv *env, jobjectArray array) {
    jsize count = (*env)->GetArrayLength(env, array);
    NSMutableArray<NSString *> *result = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
    for (jsize i = 0; i < count; i++) {
        jstring item = (jstring)(*env)->GetObjectArrayElement(env, array, i);
        NSString *identifier = FromJava(env, item);
        if ([identifier hasPrefix:ReminderPrefix]) [result addObject:identifier];
        (*env)->DeleteLocalRef(env, item);
    }
    return result;
}

@interface SimplioNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end

@implementation SimplioNotificationDelegate
- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler {
    NSString *identifier = notification.request.identifier;
    if (![identifier hasPrefix:ReminderPrefix]) {
        completionHandler(0);
        return;
    }
    SendEvent(@"observed", identifier);
    completionHandler(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList);
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
 didReceiveNotificationResponse:(UNNotificationResponse *)response
          withCompletionHandler:(void (^)(void))completionHandler {
    NSString *identifier = response.notification.request.identifier;
    if ([identifier hasPrefix:ReminderPrefix]) {
        if ([response.actionIdentifier isEqualToString:UNNotificationDefaultActionIdentifier]) {
            SendEvent(@"open", identifier);
        } else if ([response.actionIdentifier isEqualToString:UNNotificationDismissActionIdentifier]) {
            SendEvent(@"observed", identifier);
        }
    }
    completionHandler();
}
@end

static SimplioNotificationDelegate *notificationDelegate;

static UNUserNotificationCenter *Center(void) {
    return [UNUserNotificationCenter currentNotificationCenter];
}

#define JNI_METHOD(name) Java_com_theunderseer_simplio_notifications_MacClassNotificationCenter_ ## name

JNIEXPORT jboolean JNICALL JNI_METHOD(nativeInitialize)(
    JNIEnv *env, jobject instance, jobject events, jboolean diagnostics
) {
    @autoreleasepool {
        if (![NSBundle.mainBundle.bundleIdentifier isEqualToString:@"com.theunderseer.simplio"]) {
            if (diagnostics) fputs("[reminders] unexpected application bundle identity\n", stderr);
            return JNI_FALSE;
        }
        diagnosticsEnabled = diagnostics;
        UNUserNotificationCenter *center;
        @try {
            center = Center();
        } @catch (NSException *error) {
            NSLog(@"Simplio could not initialize macOS notifications: %@", error.reason);
            return JNI_FALSE;
        }
        if (center == nil || (*env)->GetJavaVM(env, &jvm) != JNI_OK) return JNI_FALSE;
        jclass type = (*env)->FindClass(env, "java/util/function/BiConsumer");
        if (type == NULL) return JNI_FALSE;
        acceptEvent = (*env)->GetMethodID(env, type, "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V");
        (*env)->DeleteLocalRef(env, type);
        if (acceptEvent == NULL) return JNI_FALSE;
        eventConsumer = (*env)->NewGlobalRef(env, events);
        if (eventConsumer == NULL) return JNI_FALSE;
        notificationDelegate = [SimplioNotificationDelegate new];
        center.delegate = notificationDelegate;
        UNNotificationCategory *category =
            [UNNotificationCategory categoryWithIdentifier:ReminderCategory actions:@[] intentIdentifiers:@[]
                                                   options:UNNotificationCategoryOptionCustomDismissAction];
        [center setNotificationCategories:[NSSet setWithObject:category]];

        dispatch_async(dispatch_get_main_queue(), ^{
            NSNotificationCenter *workspace = NSWorkspace.sharedWorkspace.notificationCenter;
            for (NSString *name in @[NSWorkspaceDidWakeNotification, NSWorkspaceScreensDidWakeNotification,
                                     NSWorkspaceSessionDidBecomeActiveNotification]) {
                [workspace addObserverForName:name object:nil queue:nil
                                   usingBlock:^(NSNotification *notification) { SendEvent(@"refresh", @""); }];
            }
            [NSNotificationCenter.defaultCenter addObserverForName:NSApplicationDidBecomeActiveNotification
                object:nil queue:nil usingBlock:^(NSNotification *notification) { SendEvent(@"refresh", @""); }];
        });
        return JNI_TRUE;
    }
}

static void ReadAuthorization(jobject callback) {
    [Center() getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
        NSString *status;
        switch (settings.authorizationStatus) {
            case UNAuthorizationStatusAuthorized: status = @"ALLOWED"; break;
            case UNAuthorizationStatusNotDetermined: status = @"NOT_DETERMINED"; break;
            default: status = @"DENIED"; break;
        }
        Complete(callback, status, nil);
    }];
}

JNIEXPORT void JNICALL JNI_METHOD(nativeAuthorization)(JNIEnv *env, jobject instance, jobject callback) {
    @autoreleasepool { ReadAuthorization((*env)->NewGlobalRef(env, callback)); }
}

JNIEXPORT void JNICALL JNI_METHOD(nativeRequestAuthorization)(JNIEnv *env, jobject instance, jobject callback) {
    @autoreleasepool {
        jobject global = (*env)->NewGlobalRef(env, callback);
        [Center() requestAuthorizationWithOptions:UNAuthorizationOptionAlert
                               completionHandler:^(BOOL granted, NSError *error) {
            if (error != nil) Complete(global, @"ERROR", nil);
            else ReadAuthorization(global);
        }];
    }
}

JNIEXPORT void JNICALL JNI_METHOD(nativePending)(JNIEnv *env, jobject instance, jobject callback) {
    @autoreleasepool {
        jobject global = (*env)->NewGlobalRef(env, callback);
        [Center() getPendingNotificationRequestsWithCompletionHandler:^(NSArray<UNNotificationRequest *> *requests) {
            NSMutableArray<NSString *> *result = [NSMutableArray array];
            for (UNNotificationRequest *request in requests) {
                if (![request.identifier hasPrefix:ReminderPrefix]) continue;
                NSString *revision = request.content.userInfo[RevisionKey] ?: @"";
                [result addObject:[NSString stringWithFormat:@"%@|%@", request.identifier, revision]];
            }
            Complete(global, nil, result);
        }];
    }
}

JNIEXPORT void JNICALL JNI_METHOD(nativeDelivered)(JNIEnv *env, jobject instance, jobject callback) {
    @autoreleasepool {
        jobject global = (*env)->NewGlobalRef(env, callback);
        [Center() getDeliveredNotificationsWithCompletionHandler:^(NSArray<UNNotification *> *notifications) {
            NSMutableArray<NSString *> *result = [NSMutableArray array];
            for (UNNotification *notification in notifications) {
                NSString *identifier = notification.request.identifier;
                if ([identifier hasPrefix:ReminderPrefix]) [result addObject:identifier];
            }
            Complete(global, nil, result);
        }];
    }
}

JNIEXPORT void JNICALL JNI_METHOD(nativeRemove)(
    JNIEnv *env, jobject instance, jobjectArray pending, jobjectArray delivered
) {
    @autoreleasepool {
        [Center() removePendingNotificationRequestsWithIdentifiers:FromJavaArray(env, pending)];
        [Center() removeDeliveredNotificationsWithIdentifiers:FromJavaArray(env, delivered)];
    }
}

JNIEXPORT void JNICALL JNI_METHOD(nativeAdd)(
    JNIEnv *env, jobject instance, jstring identifier, jstring revision, jstring title, jstring body,
    jlong endsAt, jboolean immediately, jobject callback
) {
    @autoreleasepool {
        UNMutableNotificationContent *content = [UNMutableNotificationContent new];
        content.title = FromJava(env, title);
        content.body = FromJava(env, body);
        content.categoryIdentifier = ReminderCategory;
        content.userInfo = @{RevisionKey: FromJava(env, revision)};

        NSDate *date = [NSDate dateWithTimeIntervalSince1970:(NSTimeInterval)endsAt];
        UNNotificationTrigger *trigger = nil;
        if (!immediately && [date timeIntervalSinceNow] > 0) {
            NSCalendar *calendar = [[NSCalendar alloc] initWithCalendarIdentifier:NSCalendarIdentifierGregorian];
            calendar.timeZone = [NSTimeZone timeZoneForSecondsFromGMT:0];
            NSDateComponents *components = [calendar components:NSCalendarUnitYear | NSCalendarUnitMonth |
                NSCalendarUnitDay | NSCalendarUnitHour | NSCalendarUnitMinute | NSCalendarUnitSecond fromDate:date];
            components.calendar = calendar;
            components.timeZone = calendar.timeZone;
            UNCalendarNotificationTrigger *scheduled =
                [UNCalendarNotificationTrigger triggerWithDateMatchingComponents:components repeats:NO];
            if (diagnosticsEnabled) {
                NSDate *next = scheduled.nextTriggerDate;
                fprintf(stderr, "[reminders] macOS trigger expected=%s, next=%s\n",
                        date.description.UTF8String, (next.description ?: @"none").UTF8String);
            }
            trigger = scheduled;
        }

        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:FromJava(env, identifier)
                                                                              content:content trigger:trigger];
        jobject global = (*env)->NewGlobalRef(env, callback);
        [Center() addNotificationRequest:request withCompletionHandler:^(NSError *error) {
            Complete(global, error.localizedDescription ?: @"", nil);
        }];
    }
}
