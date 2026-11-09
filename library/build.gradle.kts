import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.mavemPublish) apply false
    alias(libs.plugins.pluginPublish) apply false
    id("maven-publish")
}

val libraryProperties = Properties().apply {
    project.layout.projectDirectory.file("gradle.properties").asFile.inputStream().use(::load)
}

fun libraryProperty(name: String) = providers.provider {
    libraryProperties.getProperty(name) ?: error("Missing library gradle property '$name'")
}

val publishToMaven = libraryProperties.getProperty("publishToMaven")?.toBoolean() == true
val jitpack = providers.gradleProperty("jitpack").orNull == "true"
val publishedVersion = providers.gradleProperty("version").orNull
    ?.takeIf { it.isNotBlank() && it != "unspecified" }
    ?: libraryProperty("VERSION_NAME").get()
val publishedGroup = providers.gradleProperty("group").orNull
    ?.takeIf { it.isNotBlank() }
    ?: libraryProperty("GROUP").get()

if (publishToMaven) {
    pluginManager.apply("com.vanniktech.maven.publish")
    configure<MavenPublishBaseExtension> {
        publishToMavenCentral()
    }
} else if (jitpack) {
    group = publishedGroup
    version = publishedVersion
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                artifactId = "GarbageCode"
            }
        }
    }
} else {
    pluginManager.apply("com.gradle.plugin-publish")
    group = publishedGroup
    version = publishedVersion
    configure<GradlePluginDevelopmentExtension> {
        website.set(libraryProperty("POM_URL"))
        vcsUrl.set(libraryProperty("POM_SCM_URL"))
        plugins {
            create("androidJunkCode") {
                id = "io.github.qq549631030.android-junk-code"
                implementationClass = "cn.hx.plugin.junkcode.plugin.AndroidJunkCodePlugin"
                displayName = "AndroidJunkCode plugin"
                description = libraryProperty("POM_DESCRIPTION").get()
                tags.set(listOf("android", "generate", "junk", "code"))
            }
        }
    }
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                artifactId = "GarbageCode"
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(libs.android.gradlePlugin.api)
    implementation(libs.javapoet)
}