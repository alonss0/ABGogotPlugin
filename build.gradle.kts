buildscript {
    val agp_version by extra("8.8.1")
}
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.library") version "8.8.1" apply false
    id("org.jetbrains.kotlin.android") version "1.8.0" apply false
}
