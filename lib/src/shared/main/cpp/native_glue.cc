//
// native_glue.cc
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "native_glue.hh"
#include <assert.h>
#include <queue>
#include <new>
#include <codecvt>
#include <locale>

using namespace litecore;
using namespace litecore::jni;
using namespace std;

// Java uses Modified-UTF-8, not UTF-8: Attempting to decode a real UTF-8 string will cause a failure that looks like:
//   art/runtime/check_jni.cc:65] JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8: illegal start byte ...
//   art/runtime/check_jni.cc:65]     string: ...
//   art/runtime/check_jni.cc:65]     in call to NewStringUTF
// See:
//   https://stackoverflow.com/questions/35519823/jni-detected-error-in-application-input-is-not-valid-modified-utf-8-illegal-st
// The strategy here is to use standard C functions to convert the UTF-8 directly to UTF-16, which Java handles nicely.
// The following two functions are derived from this code:
//   https://github.com/incanus/android-jni/blob/master/app/src/main/jni/JNI.cpp#L57-L86
// !! Creating the wstring_convert is expensive.  It would be nice to create one
//    and to re-use it.  It is *NOT*, however, threadsafe.
jstring litecore::jni::UTF8ToJstring(JNIEnv *env, const char *s, size_t size) {
    std::u16string ustr;
    try {
        #ifdef _MSC_VER
            auto tmpstr = std::wstring_convert<std::codecvt_utf8_utf16<int16_t>, int16_t>().from_bytes(s, s + size);
            ustr = reinterpret_cast<const char16_t *>(tmpstr.data());
        #else
            ustr = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().from_bytes(s, s + size);
        #endif
    }
    catch (const std::bad_alloc &x) {
        C4Error error = {LiteCoreDomain, kC4ErrorMemoryError, 0};
        throwError(env, error);
        return NULL;
    }
    catch (const std::exception &x) {
        C4Error error = {LiteCoreDomain, kC4ErrorCorruptData, 0};
        throwError(env, error);
        return NULL;
    }

    auto jstr = env->NewString(reinterpret_cast<const jchar *>(ustr.c_str()), ustr.size());
    if (jstr == nullptr) {
        C4Error error = {LiteCoreDomain, kC4ErrorMemoryError, 0};
        throwError(env, error);
        return NULL;
    }

    return jstr;
}

// ??? Callers can't handle exceptions so we just ignore errors and return an empty string.
std::string litecore::jni::JstringToUTF8(JNIEnv *env, jstring jstr) {
    jsize len = env->GetStringLength(jstr);
    if (len < 0)
        return std::string();

    std::string str;

    const jchar *chars = env->GetStringChars(jstr, nullptr);
    if (chars == nullptr) {
        str = std::string();
    } else {
        try {
            #ifdef _MSC_VER
                str = std::wstring_convert<std::codecvt_utf8_utf16<int16_t>, int16_t>()
                                    .to_bytes(reinterpret_cast<const int16_t *>(chars),
                                              reinterpret_cast<const int16_t *>(chars + len));
            #else
                str = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>()
                                    .to_bytes(reinterpret_cast<const char16_t *>(chars),
                                              reinterpret_cast<const char16_t *>(chars + len));
            #endif

        }
        catch (const std::exception &x) {
            str = std::string();
        }
    }

    env->ReleaseStringChars(jstr, chars);

    return str;
}

/*
 * Will be called by JNI when the library is loaded
 *
 * NOTE:
 *  Resources allocated here are never explicitly released.
 *  We rely on system to free all global refs when it goes away,
 *  the pairing function JNI_OnUnload() will never get called at all.
 */
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved) {
    JNIEnv *env;
    if (jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK
        && initC4Observer(env)
        && initC4Replicator(env)
        && initC4Socket(env)) {
        assert(gJVM == nullptr);
        gJVM = jvm;
        return JNI_VERSION_1_6;
    } else {
        return JNI_ERR;
    }
}

namespace litecore {
    namespace jni {

        JavaVM *gJVM;

        int attachCurrentThread(JNIEnv** env) {
#ifdef JNI_VERSION_1_8
            return gJVM->AttachCurrentThread(reinterpret_cast<void **>(env), NULL);
#else
            return gJVM->AttachCurrentThread(env, NULL);
#endif
        }

        void deleteGlobalRef(jobject gRef) {
            JNIEnv *env = NULL;
            jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
            if (getEnvStat == JNI_OK) {
                env->DeleteGlobalRef(gRef);
            } else if (getEnvStat == JNI_EDETACHED) {
                if (attachCurrentThread(&env) == 0) {
                    env->DeleteGlobalRef(gRef);
                }
            }
        }

        jstringSlice::jstringSlice(JNIEnv *env, jstring js) {
            assert(env != nullptr);
            if (js != nullptr) {
                _str = JstringToUTF8(env, js);
                _slice = FLStr(_str.c_str());
            } else {
                _slice = kFLSliceNull;
            }
        }

        const char *jstringSlice::c_str() {
            return (const char *)_slice.buf;
        };

        // ATTN: In critical, should not call any other JNI methods.
        // http://docs.oracle.com/javase/6/docs/technotes/guides/jni/spec/functions.html
        jbyteArraySlice::jbyteArraySlice(JNIEnv *env, jbyteArray jbytes, bool critical)
            : jbyteArraySlice(env, jbytes, (size_t) (!jbytes ? 0 : env->GetArrayLength(jbytes)), critical) { }
        jbyteArraySlice::jbyteArraySlice(JNIEnv *env, jbyteArray jbytes, size_t length, bool critical)
                : _env(env),
                  _jbytes(jbytes),
                  _critical(critical) {
            if (!jbytes || length <= 0) {
                _slice = kFLSliceNull;
                return;
            }

            void* data;
            if (critical) {
                data = env->GetPrimitiveArrayCritical(jbytes, NULL);
            } else {
                data = env->GetByteArrayElements(jbytes, NULL);
            }

            _slice = { data, length };
        }

        jbyteArraySlice::~jbyteArraySlice() {
            if (_slice.buf) {
                if (_critical) {
                    _env->ReleasePrimitiveArrayCritical(_jbytes, (void *) _slice.buf, JNI_ABORT);
                } else {
                    _env->ReleaseByteArrayElements(_jbytes, (jbyte *) _slice.buf, JNI_ABORT);
                }
            }
        }

        FLSliceResult jbyteArraySlice::copy(JNIEnv *env, jbyteArray jbytes) {
            jbyteArraySlice bytes(env, jbytes, true);
            return FLSlice_Copy(bytes);

        }

        void throwError(JNIEnv *env, C4Error error) {
            if (env->ExceptionOccurred())
                return;
            jclass xclass = env->FindClass("com/couchbase/lite/LiteCoreException");
            assert(xclass); // if we can't even throw an exception, we're really fuxored
            jmethodID m = env->GetStaticMethodID(xclass, "throwException", "(IILjava/lang/String;)V");
            assert(m);

            C4SliceResult msgSlice = c4error_getMessage(error);
            jstring msg = toJString(env, msgSlice);
            c4slice_free(msgSlice);

            env->CallStaticVoidMethod(xclass, m, (jint) error.domain, (jint) error.code, msg);
        }

        jstring toJString(JNIEnv *env, C4Slice s) {
            if (s.buf == nullptr)
                return nullptr;
            return UTF8ToJstring(env, (char *) s.buf, s.size);
        }

        jstring toJString(JNIEnv *env, C4SliceResult s) {
            return toJString(env, (C4Slice) s);
        }

        jbyteArray toJByteArray(JNIEnv *env, C4Slice s) {
            if (s.buf == nullptr)
                return nullptr;
            // NOTE: Local reference is taken care by JVM.
            // http://docs.oracle.com/javase/6/docs/technotes/guides/jni/spec/functions.html#global_local
            jbyteArray array = env->NewByteArray((jsize) s.size);
            if (array)
                env->SetByteArrayRegion(array, 0, (jsize) s.size, (const jbyte *) s.buf);
            return array;
        }

        jbyteArray toJByteArray(JNIEnv *env, C4SliceResult s) {
            return toJByteArray(env, (C4Slice) s);
        }

        bool getEncryptionKey(JNIEnv *env, jint keyAlg, jbyteArray jKeyBytes,
                              C4EncryptionKey *outKey) {
            outKey->algorithm = (C4EncryptionAlgorithm) keyAlg;
            if (keyAlg != kC4EncryptionNone) {
                jbyteArraySlice keyBytes(env, jKeyBytes);
                FLSlice keySlice = keyBytes;
                if (!keySlice.buf || keySlice.size > sizeof(outKey->bytes)) {
                    throwError(env, C4Error{LiteCoreDomain, kC4ErrorCrypto});
                    return false;
                }
                memset(outKey->bytes, 0, sizeof(outKey->bytes));
                memcpy(outKey->bytes, keySlice.buf, keySlice.size);
            }
            return true;
        }
    }
}
