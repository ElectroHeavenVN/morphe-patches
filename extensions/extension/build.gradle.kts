extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "ehvn.ext"

    defaultConfig {
        multiDexEnabled = false
    }
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}