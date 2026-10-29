plugins {
    `kotlin-dsl`
}

// M6-fix1: kotlin-dsl needs its own compileClasspath repositories
// (buildSrc has an isolated build; root repositories do not apply).
repositories {
    mavenCentral()
    gradlePluginPortal()
}
