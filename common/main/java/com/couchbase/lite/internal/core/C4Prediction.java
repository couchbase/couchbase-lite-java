package com.couchbase.lite.internal.core;

public final class C4Prediction {
    private C4Prediction() {}

    public static void register(String name, C4PredictiveModel model) { registerModel(name, model); }

    public static void unregister(String name) { unregisterModel(name); }

    private static native void registerModel(String name, C4PredictiveModel model);

    private static native void unregisterModel(String name);
}
