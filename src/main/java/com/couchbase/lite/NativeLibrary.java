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
import java.util.concurrent.atomic.AtomicBoolean;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

final class NativeLibrary {
    private static final String[] LIBRARIES = { "LiteCore", "LiteCoreJNI" };

    private static final String LIBS_RES_PATH = "/libs";

    private static final String TMP_DIR_NAME = "com.couchbase.lite.java";

    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    static void load() {
        if (LOADED.getAndSet(true)) { return; }

        for (String lib : LIBRARIES) { load(lib); }
    }

    private static void load(String libName) {
        try {
            final File libFile = extractLibrary(libName);
            System.load(libFile.getAbsolutePath());
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot extract and load library: " + libName, e);
        }
    }

    @NonNull @SuppressFBWarnings("DE_MIGHT_IGNORE")
    private static File extractLibrary(@NonNull String libName) throws IOException, InterruptedException {
        final String libResPath = getLibraryResourcePath(libName);
        final String tmpDir = CouchbaseLite.getTmpDirectory(TMP_DIR_NAME);
        final String targetDir = new File(tmpDir).getAbsolutePath();
        final File targetFile = new File(targetDir, System.mapLibraryName(libName));

        if (targetFile.exists() && !targetFile.delete()) {
            throw new IllegalStateException("Failed to delete the existing native library at " +
                        targetFile.getAbsolutePath());
        }

        // Extract the library to the target directory:
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = NativeLibrary.class.getResourceAsStream(libResPath);
            if (in == null) { throw new IllegalStateException("Native library not found at " + libResPath); }

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
            Runtime.getRuntime().exec(
                    new String[] {"chmod", "755", targetFile.getAbsolutePath()}).waitFor();
        }
        return targetFile;
    }

    @NonNull
    private static String getLibraryResourcePath(@NonNull String libraryName) {
        // Root native library folder.
        String path = LIBS_RES_PATH;

        // OS:
        final String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) { path += "/linux"; }
        else if (osName.contains("Mac")) { path += "/osx"; }
        else if (osName.contains("Windows")) { path += "/windows"; }
        else { path += "/" + osName; }

        // Arch:
        String archName  = null;
        if (osName.contains("Windows")) { archName = "x86_64"; }
        else {
            archName = System.getProperty("os.arch");
            archName = archName.replaceAll("\\W", "");
            archName = archName.replace("-", "_");
        }
        path += '/' + archName;

        // Platform specific name part of path.
        path += '/' + System.mapLibraryName(libraryName);
        return path;
    }

    NativeLibrary() { }
}
