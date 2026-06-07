-allowaccessmodification
-repackageclasses

# CMFA core exposes JNI entry points used by native code.
# Keep these bindings exact while allowing the rest of the app to shrink aggressively.
-keep class com.github.kr328.clash.core.bridge.** { *; }

# Rhino includes optional Java Bean JSON helpers. Android does not provide java.beans,
# and the profile override path only exchanges JSON strings with the JS runtime.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
