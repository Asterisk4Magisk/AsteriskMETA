-allowaccessmodification
-repackageclasses

# CMFA core exposes JNI entry points used by native code.
# Keep these bindings exact while allowing the rest of the app to shrink aggressively.
-keep class com.github.kr328.clash.core.bridge.** { *; }
