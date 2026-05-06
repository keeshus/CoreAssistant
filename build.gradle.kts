buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.21")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.7")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
