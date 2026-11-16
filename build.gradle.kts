plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// JVM @argfile parsing strips unquoted single quotes (') from -cp paths, breaking test workers
// when the workspace path itself contains an apostrophe (e.g. "SnowDo's").
if (rootDir.absolutePath.contains("'")) {
    val safeBuildRoot = file("${System.getProperty("java.io.tmpdir")}/snowdos-build")
    allprojects {
        layout.buildDirectory.set(file("$safeBuildRoot/${project.name}"))
    }
}

