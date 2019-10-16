//
// C4ReplicatorStatus.java
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


/**
 * WARNING!
 * This class and its members are referenced by name, from native code.
 *
 * Keep this class immutable.
 */
public final class C4ReplicatorStatus {
    public static final class ActivityLevel {
        public static final int STOPPED = 0;
        public static final int OFFLINE = 1;
        public static final int CONNECTING = 2;
        public static final int IDLE = 3;
        public static final int BUSY = 4;

        private ActivityLevel() {}
    }

    private final int activityLevel;            // Referenced from native code: C4ReplicatorStatus.ActivityLevel
    private final long progressUnitsCompleted;  // Referenced from native code: C4Progress.unitsCompleted
    private final long progressUnitsTotal;      // Referenced from native code: C4Progress.unitsTotal
    private final long progressDocumentCount;   // Referenced from native code: C4Progress.documentCount
    private final int errorDomain;              // Referenced from native code: C4Error.domain
    private final int errorCode;                // Referenced from native code: C4Error.code
    private final int errorInternalInfo;        // Referenced from native code: C4Error.internal_info

    // Called from native code
    public C4ReplicatorStatus() { this(-1, 0, 0, 0, 0, 0, 0); }

    public C4ReplicatorStatus(int activityLevel) { this(activityLevel, 0, 0, 0, 0, 0, 0); }

    public C4ReplicatorStatus(int activityLevel, int errorDomain, int errorCode) {
        this(activityLevel, 0, 0, 0, errorDomain, errorCode, 0);
    }

    public C4ReplicatorStatus(
        int activityLevel,
        long progressUnitsCompleted,
        long progressUnitsTotal,
        long progressDocumentCount,
        int errorDomain,
        int errorCode,
        int errorInternalInfo) {
        this.activityLevel = activityLevel;
        this.progressUnitsCompleted = progressUnitsCompleted;
        this.progressUnitsTotal = progressUnitsTotal;
        this.progressDocumentCount = progressDocumentCount;
        this.errorDomain = errorDomain;
        this.errorCode = errorCode;
        this.errorInternalInfo = errorInternalInfo;
    }

    public C4ReplicatorStatus copy() {
        return new C4ReplicatorStatus(
            activityLevel,
            progressUnitsCompleted,
            progressUnitsTotal,
            progressDocumentCount,
            errorDomain,
            errorCode,
            errorInternalInfo);
    }

    public C4ReplicatorStatus copyAtlevel(int newLevel) {
        return new C4ReplicatorStatus(
            newLevel,
            progressUnitsCompleted,
            progressUnitsTotal,
            progressDocumentCount,
            errorDomain,
            errorCode,
            errorInternalInfo);
    }

    public int getActivityLevel() { return activityLevel; }

    public long getProgressUnitsCompleted() { return progressUnitsCompleted; }

    public long getProgressUnitsTotal() { return progressUnitsTotal; }

    public long getProgressDocumentCount() { return progressDocumentCount; }

    public int getErrorDomain() { return errorDomain; }

    public int getErrorCode() { return errorCode; }

    public int getErrorInternalInfo() { return errorInternalInfo; }

    public C4Error getC4Error() { return new C4Error(errorDomain, errorCode, errorInternalInfo); }

    @Override
    public String toString() {
        return "C4ReplicatorStatus{"
            + "level=" + activityLevel
            + ", completed=" + progressUnitsCompleted
            + ", total=" + progressUnitsTotal
            + ", #docs=" + progressDocumentCount
            + ", domain=" + errorDomain
            + ", code=" + errorCode
            + ", info=" + errorInternalInfo
            + '}';
    }
}
