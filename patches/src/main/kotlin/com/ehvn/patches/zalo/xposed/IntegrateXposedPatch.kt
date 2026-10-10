package com.ehvn.patches.zalo.xposed

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import com.ehvn.patches.shared.npatch.NPatchRunner
import com.ehvn.patches.zalo.shared.Constants.COMPATIBILITY_ZALO

private val providerOption = booleanOption(
    key = "provider",
    default = false,
    title = "Inject file provider",
    description = "Injects a file provider to manage internal app data files. Useful for backup/restore Zalo data.",
)

private val cleartextOption = booleanOption(
    key = "cleartext",
    default = false,
    title = "Allow cleartext traffic",
    description = "Forces android:usesCleartextTraffic=\"true\" in manifest to allow plain HTTP traffic.",
)

private val useMicroGOption = booleanOption(
    key = "useMicroG",
    default = false,
    title = "Use MicroG",
    description = "Redirects GMS calls to MicroG.",
)

@Suppress("unused")
val integrateXposedPatch = resourcePatch(
    name = "Integrate ZaloXposed",
    description = "Integrates ZaloXposed into Zalo APK using NPatch CLI.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_ZALO)

    val provider by providerOption()
    val cleartext by cleartextOption()
    val useMicroG by useMicroGOption()

    finalize {
        val targetApk = NPatchRunner.resolveTargetApk(this)
        NPatchRunner.run(
            targetApk = targetApk,
            injectProvider = provider ?: false,
            usesCleartextTraffic = cleartext ?: false,
            useMicroG = useMicroG ?: false,
        )
    }
}


