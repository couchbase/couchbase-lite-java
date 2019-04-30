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

import javax.annotation.Nullable;


public class Conflict {
    @Nullable
    private final Document localDocument;
    @Nullable
    private final Document remoteDocument;

    Conflict(@Nullable Document localDocument, @Nullable Document remoteDocument) {
        this.localDocument = localDocument;
        this.remoteDocument = remoteDocument;
    }

    @Nullable
    private Document getLocalDocument() { return localDocument; }

    @Nullable
    private Document getRemoteDocument() { return remoteDocument; }
}
