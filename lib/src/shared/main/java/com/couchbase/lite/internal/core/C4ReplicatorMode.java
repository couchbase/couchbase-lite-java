//
// C4ReplicatorMode.java
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
package com.couchbase.lite.internal.core;

// How to replicate, in either direction
public enum  C4ReplicatorMode {
    C4_DISABLED(0),   // Do not allow this direction
    C4_PASSIVE(1),    // Allow peer to initiate this direction
    C4_ONE_SHOT(2),   // Replicate, then stop
    C4_CONTINUOUS(3); // Keep replication active until stopped by application

    private final int val;

    C4ReplicatorMode(int val) { this.val = val; }

    public int getVal() { return val; }
}
