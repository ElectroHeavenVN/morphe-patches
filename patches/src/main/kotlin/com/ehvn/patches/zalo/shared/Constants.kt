package com.ehvn.patches.zalo.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

object Constants {
    val COMPATIBILITY_ZALO = Compatibility(
        name = "Zalo",
        packageName = "com.zing.zalo",
        appIconColor = 0x0068FF,
        targets = listOf(
            AppTarget(
                version = "26.09.01",
                versionCodes = mapOf(SupportedAbi.ARMEABI_V7A to 260901903, SupportedAbi.ARM64_V8A to 260901903),
            ),
            AppTarget(
                version = "26.10.01",
                versionCodes = mapOf(SupportedAbi.ARMEABI_V7A to 261001905, SupportedAbi.ARM64_V8A to 261001905),
                isExperimental = true,
            ),
        ),
    )
}

