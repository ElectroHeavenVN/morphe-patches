package com.ehvn.patches.shared.npatch

import java.io.File

object NPatchRunner {

    /**
     * Extracts a packaged resource from the bundle classpath to a temporary file.
     * Falls back to reading from local workspace files during development.
     */
    fun extractResource(resourcePath: String, outputFileName: String): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "morphe_npatch")
        tempDir.mkdirs()
        val outputFile = File(tempDir, outputFileName)

        val stream = NPatchRunner::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: File("patches/src/main/resources/$resourcePath").takeIf { it.exists() }?.inputStream()
            ?: File("d:/Working Repositories/morphe-patches/patches/src/main/resources/$resourcePath").takeIf { it.exists() }?.inputStream()
            ?: error("Resource not found: $resourcePath")

        stream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outputFile
    }

    fun extractResourceOrNull(resourcePath: String, outputFileName: String): File? {
        val stream = NPatchRunner::class.java.classLoader.getResourceAsStream(resourcePath)
            ?: File("patches/src/main/resources/$resourcePath").takeIf { it.exists() }?.inputStream()
            ?: File("d:/Working Repositories/morphe-patches/patches/src/main/resources/$resourcePath").takeIf { it.exists() }?.inputStream()
            ?: return null

        val tempDir = File(System.getProperty("java.io.tmpdir"), "morphe_npatch")
        tempDir.mkdirs()
        val outputFile = File(tempDir, outputFileName)

        stream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outputFile
    }

    /**
     * Executes NPatch with the specified options on the target APK.
     */
    fun run(
        targetApk: File,
        outputDir: File? = null,
        embedModule: File? = null,
        injectProvider: Boolean = false,
        useMicroG: Boolean = false,
        usesCleartextTraffic: Boolean = false,
    ) {
        val moduleApk = embedModule ?: extractResource("modules/app-release.apk", "app-release.apk")
        val effectiveOutputDir = outputDir ?: targetApk.parentFile ?: File(".")

        try {
            println("[NPatchRunner] Running NPatch in-process (bypassing JCommander)...")
            runInProcess(
                targetApk = targetApk,
                outputDir = effectiveOutputDir,
                moduleApk = moduleApk,
                injectProvider = injectProvider,
                useMicroG = useMicroG,
                usesCleartextTraffic = usesCleartextTraffic,
            )
        } catch (e: Throwable) {
            println("[NPatchRunner] In-process execution error: ${e.message}. Trying CLI process fallback...")
            val npatchJar = extractResourceOrNull("bin/npatch.jar", "npatch.jar")
                ?: File("patches/libs/npatch.jar")
            val args = mutableListOf(
                "-m", moduleApk.absolutePath,
                "--injectdex",
                "-npa",
                "-l", "0",
                "-f",
            )
            if (injectProvider) args.add("--provider")
            if (useMicroG) args.add("--useMicroG")
            if (usesCleartextTraffic) args.add("--cleartext")
            args.add("-o")
            args.add(effectiveOutputDir.absolutePath)
            args.add(targetApk.absolutePath)

            try {
                runViaProcess(npatchJar, null, args)
            } catch (_: Throwable) {
                throw e
            }
        }
    }

    private fun isAndroidEnvironment(): Boolean {
        return System.getProperty("java.vendor", "").contains("Android", ignoreCase = true) ||
            System.getProperty("java.vm.vendor", "").contains("Android", ignoreCase = true) ||
            File("/system/bin/app_process").exists()
    }

    private fun runInProcess(
        targetApk: File,
        outputDir: File,
        moduleApk: File,
        injectProvider: Boolean,
        useMicroG: Boolean,
        usesCleartextTraffic: Boolean,
    ) {
        val npatchClass = try {
            Class.forName("top.nkbe.npatch.patch.NPatch")
        } catch (e: ClassNotFoundException) {
            error("Cannot find top.nkbe.npatch.patch.NPatch in classpath: ${e.message}")
        }

        // Allocate instance without invoking constructor to bypass JCommander.newBuilder()
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafeField.isAccessible = true
        val unsafe = theUnsafeField.get(null)
        val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
        val npatch = allocateMethod.invoke(unsafe, npatchClass)

        // Set private fields via reflection
        fun setField(name: String, value: Any?) {
            var cls: Class<*>? = npatchClass
            while (cls != null && cls != Any::class.java) {
                try {
                    val field = cls.getDeclaredField(name)
                    field.isAccessible = true
                    field.set(npatch, value)
                    return
                } catch (_: NoSuchFieldException) {
                    cls = cls.superclass
                }
            }
        }

        val loggerClass = try {
            Class.forName("top.nkbe.npatch.patch.util.JavaLogger")
        } catch (_: ClassNotFoundException) {
            null
        }
        val logger = loggerClass?.getDeclaredConstructor()?.newInstance()

        setField("logger", logger)
        setField("apkPaths", arrayListOf(targetApk.absolutePath))
        setField("outputPath", outputDir.absolutePath)
        setField("forceOverwrite", true)
        setField("modules", arrayListOf(moduleApk.absolutePath))
        setField("injectDex", true)
        setField("useNpatchKeystore", true)
        setField("useFpaKeystore", false)
        setField("useManager", false)
        setField("sigbypassLevel", 0)
        setField("isInjectProvider", injectProvider)
        setField("useMicroG", useMicroG)
        setField("usesCleartextTraffic", usesCleartextTraffic)
        setField("outputLog", true)
        setField("verbose", true)
        setField("newPackageName", "")
        setField("installerSource", "")

        println("[NPatchRunner] Calling NPatch.doCommandLine directly (bypassing JCommander)...")
        val doCommandLineMethod = npatchClass.getMethod("doCommandLine")
        try {
            doCommandLineMethod.invoke(npatch)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun runViaProcess(npatchJar: File, bcprovJar: File?, args: List<String>) {
        val javaExe = findJavaExecutable()
        val cpFiles = listOfNotNull(npatchJar.absolutePath, bcprovJar?.absolutePath)
        val classpath = cpFiles.joinToString(File.pathSeparator)

        val command = mutableListOf(
            javaExe,
            "-cp", classpath,
            "top.nkbe.npatch.patch.NPatch",
        )
        command.addAll(args)

        println("[NPatchRunner] Executing: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command).start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (stdout.isNotBlank()) {
            println("[NPatchRunner] Output:\n$stdout")
        }
        if (stderr.isNotBlank()) {
            System.err.println("[NPatchRunner] Stderr:\n$stderr")
        }

        if (exitCode != 0) {
            error("NPatch failed with exit code $exitCode:\n$stderr\n$stdout")
        }
    }

    private fun findJavaExecutable(): String {
        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrBlank()) {
            val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
            val exeName = if (isWindows) "java.exe" else "java"
            val binJava = File(javaHome, "bin/$exeName")
            if (binJava.exists()) {
                return binJava.absolutePath
            }
        }
        return "java"
    }

    /**
     * Resolves the target APK file path:
     * 1. Inspects context object via recursive reflection (across all superclasses).
     * 2. Checks Morphe cache / temp directories on Android and Desktop.
     * 3. Checks command-line arguments of the running process (`sun.java.command`).
     * 4. Checks current working directory.
     */
    fun resolveTargetApk(context: Any? = null): File {
        if (context != null) {
            findApkInContext(context)?.let { return it }
        }

        // Search common Morphe cache / temp directories
        val candidateDirs = mutableListOf<File>()
        candidateDirs.add(File(System.getProperty("java.io.tmpdir", ".")))
        candidateDirs.add(File("/data/user/0/app.morphe.manager/cache"))
        candidateDirs.add(File("/data/data/app.morphe.manager/cache"))
        candidateDirs.add(File("/sdcard/Download"))
        candidateDirs.add(File("/storage/emulated/0/Download"))
        candidateDirs.add(File("."))

        for (dir in candidateDirs) {
            if (!dir.exists()) continue
            val apk = findApkInDirectory(dir, maxDepth = 4)
            if (apk != null) return apk
        }

        val sunCommand = System.getProperty("sun.java.command")
        if (!sunCommand.isNullOrBlank()) {
            val parts = sunCommand.split("\\s+".toRegex())
            for (i in parts.indices) {
                val part = parts[i].trim('"', '\'')
                if (part.endsWith(".apk", ignoreCase = true) && !part.contains("npatched", ignoreCase = true)) {
                    val prev = if (i > 0) parts[i - 1] else ""
                    if (prev != "-o" && prev != "--output" && prev != "-p" && prev != "--patches") {
                        val file = File(part)
                        if (file.exists()) return file.absoluteFile
                    }
                }
            }
        }

        error("Cannot auto-detect target APK for NPatch.")
    }

    private fun findApkInContext(context: Any): File? {
        val visited = mutableSetOf<Any>()
        return inspectObject(context, visited, 0)
    }

    private fun inspectObject(obj: Any, visited: MutableSet<Any>, depth: Int): File? {
        if (depth > 5 || !visited.add(obj)) return null

        if (obj is File) {
            if (obj.isFile && obj.extension.equals("apk", ignoreCase = true)) {
                return obj.absoluteFile
            }
            if (obj.isDirectory) {
                val apk = findApkInDirectory(obj, maxDepth = 2)
                if (apk != null) return apk
                obj.parentFile?.let { parent ->
                    val parentApk = findApkInDirectory(parent, maxDepth = 1)
                    if (parentApk != null) return parentApk
                }
            }
            return null
        }

        if (obj is Map<*, *>) {
            for (v in obj.values) {
                if (v != null) {
                    val found = inspectObject(v, visited, depth + 1)
                    if (found != null) return found
                }
            }
            return null
        }

        if (obj is Iterable<*>) {
            for (v in obj) {
                if (v != null) {
                    val found = inspectObject(v, visited, depth + 1)
                    if (found != null) return found
                }
            }
            return null
        }

        var cls: Class<*>? = obj.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                try {
                    field.isAccessible = true
                    val value = field.get(obj) ?: continue
                    val found = inspectObject(value, visited, depth + 1)
                    if (found != null) return found
                } catch (_: Throwable) {}
            }
            for (method in cls.declaredMethods) {
                if (method.parameterCount == 0 && (method.name.startsWith("get") || method.name.contains("file", ignoreCase = true) || method.name.contains("apk", ignoreCase = true) || method.name.contains("dir", ignoreCase = true))) {
                    try {
                        method.isAccessible = true
                        val value = method.invoke(obj) ?: continue
                        val found = inspectObject(value, visited, depth + 1)
                        if (found != null) return found
                    } catch (_: Throwable) {}
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun findApkInDirectory(dir: File, maxDepth: Int = 3): File? {
        try {
            val apkFiles = dir.walkTopDown().maxDepth(maxDepth).filter { f ->
                f.isFile && f.extension.equals("apk", ignoreCase = true) &&
                    !f.name.contains("npatched", ignoreCase = true)
            }.toList()

            apkFiles.firstOrNull { it.name.contains("merged", ignoreCase = true) }?.let { return it.absoluteFile }
            apkFiles.firstOrNull { it.name.contains("zalo", ignoreCase = true) }?.let { return it.absoluteFile }
            apkFiles.firstOrNull { it.name.contains("base", ignoreCase = true) }?.let { return it.absoluteFile }
            apkFiles.firstOrNull()?.let { return it.absoluteFile }
        } catch (_: Throwable) {}
        return null
    }
}


