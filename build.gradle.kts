plugins {
    id("java")
    id("org.spongepowered.gradle.plugin") version "2.0.2"
}

group = "com.brnsvr"
version = "1.5.0-1.21.8"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

dependencies {
    compileOnly("org.spongepowered:spongeapi:14.0.0")
    compileOnly("javax.inject:javax.inject:1")
}

sponge {
    apiVersion("14.0.0")
    license("GPL-3.0-or-later")
    loader {
        name("java_plain")
        version("1.0")
    }
    plugin("borderxp") {
        displayName("BorderXP")
        entrypoint("com.brnsvr.borderxp.BorderXPPlugin")
        description("Border size by XP level. Plugin by BrnSvr.")
    }
}
