// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

// Mirrors .editorconfig; Spotless does not reliably pick up ktlint rule toggles from it.
val ktlintOverrides =
    mapOf(
        "ktlint_code_style" to "android_studio",
        "max_line_length" to "120",
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        "ktlint_standard_filename" to "disabled"
    )

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintOverrides)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintOverrides)
    }
}
