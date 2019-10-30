//
// native_glue.hh
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

#ifndef native_glue_hpp
#define native_glue_hpp

#include <jni.h>
#include <string>
#include <vector>
#include <c4.h>
#include "fleece/Fleece.h"

namespace litecore {
    namespace jni {

        // Soft limit of number of local JNI refs to use. Even using PushLocalFrame(), you may not get as
        // many refs as you asked for. At least, that's what happens on Android: the new frame won't have
        // more than 512 refs available. So 200 is being conservative.
        static const jsize MaxLocalRefsToUse = 200;

        extern JavaVM *gJVM;

        int attachCurrentThread(JNIEnv** p_env);

        void deleteGlobalRef(jobject gRef);

        bool initC4Observer(JNIEnv *);   // Implemented in native_c4observer.cc
        bool initC4Replicator(JNIEnv *); // Implemented in native_c4replicator.cc
        bool initC4Socket(JNIEnv *);     // Implemented in native_c4socket.cc

        std::string JstringToUTF8(JNIEnv *env, jstring jstr);
        jstring UTF8ToJstring(JNIEnv *env, const char *s, size_t size);

        // Creates a temporary slice value from a Java String object
        class jstringSlice {
        public:
            jstringSlice(JNIEnv *env, jstring js);

            jstringSlice(jstringSlice &&s)
                : _str(std::move(s._str)), _slice(s._slice) { s._slice = kFLSliceNull; }

            operator FLSlice() { return _slice; }

            const char* c_str();
        private:
            std::string _str;
            FLSlice _slice;
        };

        // Creates a temporary slice value from a Java byte[], attempting to avoid copying
        class jbyteArraySlice {
        public:
            // Warning: If `critical` is true, you cannot make any further JNI calls (except other
            // critical accesses) until this object goes out of scope or is deleted.
            jbyteArraySlice(JNIEnv *env, jbyteArray jbytes, bool critical = false);
            jbyteArraySlice(JNIEnv *env, jbyteArray jbytes, size_t length, bool critical = false);

            ~jbyteArraySlice();

            jbyteArraySlice(jbyteArraySlice &&s) // move constructor
                    : _slice(s._slice), _env(s._env), _jbytes(s._jbytes),
                      _critical(s._critical) { s._slice = kFLSliceNull; }

            operator FLSlice() { return _slice; }

            // Copies a Java byte[] to FLSliceResult
            static FLSliceResult copy(JNIEnv *env, jbyteArray jbytes);

        private:
            FLSlice _slice;
            JNIEnv *_env;
            jbyteArray _jbytes;
            bool _critical;
        };

        // Creates a Java String from the contents of a C4Slice.

        jstring toJString(JNIEnv *, C4Slice);

        jstring toJString(JNIEnv *, C4SliceResult);

        // Creates a Java byte[] from the contents of a C4Slice.

        jbyteArray toJByteArray(JNIEnv *, C4Slice);

        jbyteArray toJByteArray(JNIEnv *, C4SliceResult);

        // Sets a Java exception based on the LiteCore error.
        void throwError(JNIEnv *, C4Error);

        // Copies an encryption key to a C4EncryptionKey. Returns false on exception.
        bool getEncryptionKey(JNIEnv *env,
                              jint keyAlg,
                              jbyteArray jKeyBytes,
                              C4EncryptionKey *outKey);

    }
}

#endif /* native_glue_hpp */
