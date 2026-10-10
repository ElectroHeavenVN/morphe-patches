package com.ehvn.patches.zalo.misc

import org.w3c.dom.Document
import org.w3c.dom.Element

const val ORG_PKGNAME = "com.zing.zalo"
private const val PERMISSION_PREFIX = "zing.zalo.permission."
private val PKGNAME_REGEX = Regex("^[a-z][\\w]*(\\.[a-z][\\w]*)+$")

fun isValidZaloPackageName(name: String?): Boolean = name != null && PKGNAME_REGEX.matches(name)

/** Rewrites manifest identities */
fun rewriteZaloPackage(document: Document, newPackage: String) {
    document.documentElement.setAttribute("package", newPackage)
    val providers = document.getElementsByTagName("provider")
    for (i in 0 until providers.length) {
        val provider = providers.item(i) as Element
        val authorities = provider.getAttribute("android:authorities")
        val rewritten = authorities.split(';').joinToString(";") { authority ->
            when {
                authority == ORG_PKGNAME -> newPackage
                authority.startsWith("$ORG_PKGNAME.") -> authority.replaceFirst("$ORG_PKGNAME.", "$newPackage.")
                else -> authority
            }
        }
        if (rewritten != authorities) provider.setAttribute("android:authorities", rewritten)
    }

    val ownedPrefix = "$ORG_PKGNAME."
    val permissionNodes = sequenceOf("permission", "uses-permission")
        .flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).asSequence().map { nodes.item(it) as Element }
        }
    permissionNodes.forEach { node ->
        val name = node.getAttribute("android:name")
        when {
            name.startsWith(ownedPrefix) -> node.setAttribute("android:name", name.replaceFirst(ownedPrefix, "$newPackage."))
            name.startsWith(PERMISSION_PREFIX) -> node.setAttribute("android:name", name.replaceFirst(PERMISSION_PREFIX, "$newPackage.permission."))
        }
    }

    val elements = document.getElementsByTagName("*")
    for (i in 0 until elements.length) {
        val element = elements.item(i) as Element
        val permission = element.getAttribute("android:permission")
        if (permission.startsWith(PERMISSION_PREFIX)) {
            element.setAttribute(
                "android:permission",
                permission.replaceFirst(PERMISSION_PREFIX, "$newPackage.permission."),
            )
        }
    }
}
