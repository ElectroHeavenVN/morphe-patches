package com.ehvn.patches.zalo.misc

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import com.ehvn.patches.zalo.shared.Constants.COMPATIBILITY_ZALO
import com.ehvn.patches.zalo.xposed.integrateXposedPatch

private val cloneNumberOption = stringOption(
    key = "cloneNumber",
    default = "1",
    title = "Clone number",
    description = "The clone number appended to the package name and app name (e.g. 2 -> Zalo Morphe 2). Leave empty to use the default name.",
    required = false,
) { it.isNullOrBlank() || it.all { char -> char.isDigit() } }

@Suppress("unused")
val cloneZaloPatch = resourcePatch(
    name = "Clone app",
    description = "Change Zalo package name and update the app name to allow multiple installations.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_ZALO)
    dependsOn(integrateXposedPatch)

    val cloneNumber by cloneNumberOption()

    finalize {
        val parsedNum = cloneNumber?.trim()?.toIntOrNull()
        val (targetPackageName, targetAppName) = if (parsedNum == null || parsedNum < 1) {
            "$ORG_PKGNAME.morphe" to "Zalo Morphe"
        } else {
            "$ORG_PKGNAME.morphe_$parsedNum" to "Zalo Morphe $parsedNum"
        }

        document("AndroidManifest.xml").use { document ->
            rewriteZaloPackage(document, targetPackageName)
            applyZaloAppName(document, targetAppName)
        }
    }
}



