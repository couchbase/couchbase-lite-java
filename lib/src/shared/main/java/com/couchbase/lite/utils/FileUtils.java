//
// FileUtils.java
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
package com.couchbase.lite.utils;

import android.support.annotation.NonNull;

import java.io.File;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.utils.Preconditions;


public final class FileUtils {
    private FileUtils() { }

    public static boolean eraseFileOrDir(@NonNull String fileOrDirectory) {
        Preconditions.assertNotNull(fileOrDirectory, "file or directory");
        return eraseFileOrDir(new File(fileOrDirectory));
    }

    public static boolean eraseFileOrDir(File fileOrDirectory) {
        Preconditions.assertNotNull(fileOrDirectory, "file or directory");
        return deleteRecursive(fileOrDirectory);
    }

    @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"})
    public static boolean deleteContents(File fileOrDirectory) {
        if ((fileOrDirectory == null) || (!fileOrDirectory.isDirectory())) { return true; }

        final File[] contents = fileOrDirectory.listFiles();
        if (contents == null) { return true; }

        for (File file : contents) {
            if (!deleteRecursive(file)) { return false; }
        }

        return true;
    }

    public static boolean setPermissionRecursive(File fileOrDirectory, boolean readable, boolean writable) {
        if (fileOrDirectory.isDirectory()) {
            final File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) { setPermissionRecursive(child, readable, writable); }
            }
        }
        return fileOrDirectory.setReadable(readable) && fileOrDirectory.setWritable(writable);
    }

    private static boolean deleteRecursive(File fileOrDirectory) {
        return (!fileOrDirectory.exists()) || (deleteContents(fileOrDirectory) && fileOrDirectory.delete());
    }
}
