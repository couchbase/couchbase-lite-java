//
// NativeLibrary.java
//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * For extracting and loading native libraries for couchbase-lite-java.
 */
final class NativeLibrary {
    private static final String[] LIBRARIES = { "LiteCore", "LiteCoreJNI" };

    private static final String LIBS_RES_BASE_DIR = "/libs";

    private static final String TARGET_BASE_DIR = "com.couchbase.lite.java/native";

    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    /**
     * Extracts and loads native libraries.
     */
    static void load() {
        if (LOADED.getAndSet(true)) { return; }
        try {
            final String[] libPaths = Arrays.stream(LIBRARIES).map(lib -> getResourcePath(lib)).toArray(String[]::new);
            final String targetDir = getTargetDirectory(libPaths);
            for (String path : libPaths) {
                System.load(extract(path, targetDir).getAbsolutePath());
            }
        }
        catch (Throwable th) {
            final String platform = System.getProperty("os.name") + "/" + System.getProperty("os.arch");
            throw new IllegalStateException("Cannot load native library for " + platform, th);
        }
    }

    /**
     * Returns a target directory for extracting the native libraries into. The structure of the
     * directory will be <System Temp Directory>/com.couchbase.lite.java/native/<MD5-Hash>.
     * The MD5-Hash is the combined MD5 hash of the hashes of all native libraries.
     */
    @NonNull @SuppressFBWarnings("DE_MIGHT_IGNORE")
    private static String getTargetDirectory(@NonNull String[] libPaths)
        throws NoSuchAlgorithmException, IOException {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        for (String path : libPaths) {
            InputStream in = null;
            try {
                in = NativeLibrary.class.getResourceAsStream(path + ".MD5");
                if (in == null) { throw new IOException("Cannot find MD5 for library at " + path); }
                final byte[] buffer = new byte[128];
                int bytesRead = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            finally {
                if (in != null) { try { in.close(); } catch (IOException e) { } }
            }
        }
        final String md5 =  String.format("%032x", new BigInteger(1, md.digest()));
        return new File(CouchbaseLite.getTmpDirectory(TARGET_BASE_DIR), md5).getAbsolutePath();
    }

    /**
     * Extracts the given path to the native library in the resource directory into the target directory.
     * If the native library already exists in the target library, the existing native library will be used.
     */
    @NonNull @SuppressFBWarnings("DE_MIGHT_IGNORE")
    private static File extract(@NonNull String libResPath, @NonNull String targetDir)
        throws IOException, InterruptedException {
        final File targetFile = new File(targetDir, new File(libResPath).getName());
        if (targetFile.exists()) { return targetFile; }

        final File dir = new File(targetDir);
        if (!dir.mkdirs() && !dir.exists()) {
            throw new IOException("Cannot create target directory: " + dir.getAbsolutePath());
        }

        // Extract the library to the target directory:
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = NativeLibrary.class.getResourceAsStream(libResPath);
            if (in == null) { throw new IOException("Native library not found at " + libResPath); }

            out = new FileOutputStream(targetFile);
            final byte[] buffer = new byte[1024];
            int bytesRead = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        finally {
            if (in != null) { try { in.close(); } catch (IOException e) { } }
            if (out != null) { try { out.close(); } catch (IOException e) { } }
        }

        // On non-windows systems set up permissions for the extracted native library.
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            Runtime.getRuntime().exec(new String[] {"chmod", "755", targetFile.getAbsolutePath()}).waitFor();
        }
        return targetFile;
    }

    /**
     * Returns the path in the resource directory where the native libraries are located.
     */
    @NonNull
    private static String getResourcePath(@NonNull String libraryName) {
        // Root native library folder.
        String path = LIBS_RES_BASE_DIR;

        // OS:
        final String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) { path += "/linux"; }
        else if (osName.contains("Mac")) { path += "/macos"; }
        else if (osName.contains("Windows")) { path += "/windows"; }
        else { path += "/" + osName; }

        // Arch:
        final String archName  = "x86_64";
        path += '/' + archName;

        // Platform specific name part of path.
        path += '/' + System.mapLibraryName(libraryName);
        return path;
    }

    NativeLibrary() { }
}
