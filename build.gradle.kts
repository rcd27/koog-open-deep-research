import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("multiplatform") version "2.1.20" apply false
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

subprojects {
    extensions.configure<KtlintExtension> {
        outputToConsole = true
        coloredOutput = true
    }
}
