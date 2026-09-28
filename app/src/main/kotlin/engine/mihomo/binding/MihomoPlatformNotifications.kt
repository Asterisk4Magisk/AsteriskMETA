package engine.mihomo.binding

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

// The released AAR predates these notifications. Resolve the optional extension
// once so upgrading the independent core enables it without breaking older AARs.
internal class MihomoPlatformNotifications private constructor(
    private val dnsChanged: Method,
    private val installedAppsChanged: Method,
) {
    fun notifyDnsChanged(snapshot: String) = invoke(dnsChanged, snapshot)

    fun notifyInstalledAppsChanged(snapshot: String) = invoke(installedAppsChanged, snapshot)

    private fun invoke(method: Method, snapshot: String) {
        try {
            method.invoke(null, snapshot)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    companion object {
        fun discover(core: Class<*>): MihomoPlatformNotifications? {
            val dns = optionalMethod(core, "notifyDnsChanged") ?: return null
            val apps = optionalMethod(core, "notifyInstalledAppsChanged") ?: return null
            return MihomoPlatformNotifications(dns, apps)
        }

        private fun optionalMethod(core: Class<*>, name: String): Method? = try {
            core.getMethod(name, String::class.java).takeIf { Modifier.isStatic(it.modifiers) }
        } catch (_: NoSuchMethodException) {
            null
        }
    }
}
