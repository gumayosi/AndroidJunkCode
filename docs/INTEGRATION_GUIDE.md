# GarbageCode 接入说明

最后更新: 2026-11

本文给其他 Android 工程使用。按下面的坐标和插件 id 接入，不要改成旧仓库名。

| 项 | 值 |
| --- | --- |
| Maven 坐标 | `com.github.gumayosi:GarbageCode:v2.0.5` |
| 插件 id | `garbage-code` |
| 版本号 | `v2.0.5`（必须带前缀 `v`，与 Git 标签一致） |
| JitPack 页面 | https://jitpack.io/#gumayosi/GarbageCode/v2.0.5 |
| 源码 | https://github.com/gumayosi/GarbageCode |
| 扩展名 | `androidJunkCode` |
| 已验证环境 | AGP 9.2.1、Gradle 9.4.1、Kotlin 2.2.10 |

插件在每个已配置的变体编译前生成 Java、布局、drawable、字符串、Manifest 条目和 ProGuard 规则，用来降低马甲包和主包的代码相似度。生成物只进入对应变体，不改业务源码。

## 1. 接入前确认

- 只接到 `com.android.application` 模块。插件读取的是 `ApplicationAndroidComponentsExtension`，接到 `com.android.library` 或纯 JVM 模块会在配置阶段失败。
- 插件 id 是 `garbage-code`。不要写成 `io.github.qq549631030.android-junk-code`，那个 id 不会从 JitPack 解析到这个包。
- 坐标的 artifact 是 `GarbageCode`。`com.github.gumayosi:AndroidJunkCode` 是另一个仓库，不要混用。
- 版本必须写成 `v2.0.5`。写成 `2.0.3` 会 404。
- AGP 8.6.0 及以上走 `variant.sources.manifests` 合并生成的 Manifest。更低版本走 `ManifestMergeTask` 改写合并后的 Manifest。两种路径都会把生成的 Activity 注册进最终 Manifest。
- 插件自身字节码目标是 Java 11。构建脚本使用的 JDK 需要能跑当前 AGP（本仓库验证用的是 Android Studio 自带的 JDK 21）。
- 插件 classpath 会带上 `com.squareup:javapoet:1.13.0` 和 `org.jetbrains.kotlin:kotlin-stdlib:2.4.21`。这两项不要在 app 的 `dependencies` 里再声明。它们只在插件 classpath 上，不会进 APK 的业务依赖。

## 2. 仓库

`settings.gradle.kts` 里两处都要能访问 JitPack：`pluginManagement` 用来下载插件，`dependencyResolutionManagement` 用来解析插件 POM 里的传递依赖。

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "garbage-code") {
                useModule("com.github.gumayosi:GarbageCode:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}
```

`resolutionStrategy` 不能省。Gradle 插件门户上没有 `garbage-code` 这个 id，不映射到 `com.github.gumayosi:GarbageCode` 就会报 `UnknownPluginException`。

Groovy 的 `settings.gradle` 对应写法：

```groovy
pluginManagement {
    repositories {
        maven { url 'https://jitpack.io' }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'garbage-code') {
                useModule("com.github.gumayosi:GarbageCode:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url 'https://jitpack.io' }
        google()
        mavenCentral()
    }
}
```

如果工程把 `repositoriesMode` 设成了 `PREFER_SETTINGS` 或 `FAIL_ON_PROJECT_REPOS`，不要把 JitPack 只写在模块的 `build.gradle` 里，模块级仓库不会生效。

## 3. 声明插件

### 3.1 使用 version catalog（推荐）

`gradle/libs.versions.toml`：

```toml
[versions]
junkCode = "v2.0.5"

[plugins]
garbage-code = { id = "garbage-code", version.ref = "junkCode" }
```

根目录 `build.gradle.kts` 只声明、不应用：

```kotlin
plugins {
    alias(libs.plugins.garbage.code) apply false
}
```

application 模块 `app/build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.garbage.code)
}
```

`com.android.application` 要写在本插件前面。插件 `apply` 时会立刻查找 Android Application 扩展。

### 3.2 不使用 version catalog

根目录：

```kotlin
plugins {
    id("garbage-code") version "v2.0.5" apply false
}
```

application 模块：

```kotlin
plugins {
    id("garbage-code")
}
```

Groovy：

```groovy
plugins {
    id 'com.android.application'
    id 'garbage-code' version 'v2.0.5'
}
```

不要写成 `implementation("com.github.gumayosi:GarbageCode:v2.0.5")`。那是 Gradle 插件，不是 app 运行时依赖。

## 4. 配置变体

配置块的名字是 `androidJunkCode`。`variantConfig` 里的名字必须和 AGP 变体名一致，区分大小写。

没有 product flavor 时，变体名就是 `debug`、`release`。有 flavor 时是 `flavor + BuildType`，例如 `demoDebug`、`demoRelease`。名字对不上的变体不会生成代码，也不会报错。

```kotlin
androidJunkCode {
    debug = false
    variantConfig {
        listOf("debug", "release").forEach { variantName ->
            create(variantName) {
                packageBase = "com.example.app.junk"
                packageCount = 30
                activityCountPerPackage = 3
                excludeActivityJavaFile = false
                otherCountPerPackage = 50
                methodCountPerClass = 20
                resPrefix = "junk_"
                drawableCount = 30
                stringCount = 30
            }
        }
    }
}
```

Groovy 用容器的命名方法，不要写 `create`：

```groovy
androidJunkCode {
    debug = false
    variantConfig {
        debug {
            packageBase = "com.example.app.junk"
            packageCount = 30
            activityCountPerPackage = 3
            excludeActivityJavaFile = false
            otherCountPerPackage = 50
            methodCountPerClass = 20
            resPrefix = "junk_"
            drawableCount = 30
            stringCount = 30
        }
        release {
            packageBase = "com.example.app.junk"
            packageCount = 30
            activityCountPerPackage = 3
            excludeActivityJavaFile = false
            otherCountPerPackage = 50
            methodCountPerClass = 20
            resPrefix = "junk_"
            drawableCount = 30
            stringCount = 30
        }
    }
}
```

上面的数字只是示例。数量越大，编译越慢，dex 和资源也越大。先用小数量打通，再按相似度目标加大。

### 4.1 `debug`

`debug` 属于整个插件，不属于某个变体。设为 `true` 时，配置阶段多打两行日志：

```text
AndroidJunkCode: generate code for variant debug
AndroidJunkCode: use variant.sources.manifests.addGeneratedManifestFile for debug
```

它不会限制“只给 debug 变体生成代码”。要控制哪些变体生成，只在 `variantConfig` 里创建对应名字。

### 4.2 数量字段

| 字段 | 默认值 | 作用 |
| --- | --- | --- |
| `packageBase` | `""` | 生成包的前缀。非空时包名是 `packageBase.<生成名>`。空字符串时包名只有生成名，没有点分前缀。 |
| `packageCount` | `0` | 生成多少个包。每个包都会生成 Activity 和普通类。 |
| `activityCountPerPackage` | `0` | 每个包里的 Activity 数量。同时生成对应 layout，并写入生成的 Manifest。 |
| `excludeActivityJavaFile` | `false` | `true` 时不写 Activity 的 Java 文件，因此也不会把该 Activity 放进 Manifest。layout 仍会生成。 |
| `otherCountPerPackage` | `0` | 每个包里额外的普通 public 类数量，这些类不是 Activity。 |
| `methodCountPerClass` | `0` | 每个生成类上的方法数量。Activity 和普通类都使用这个数量。 |
| `resPrefix` | `"junk_"` | 资源名前缀，写入文件前会转成小写。drawable、string、layout 都会带上它。 |
| `drawableCount` | `0` | 生成的 drawable XML 数量，所有包共用这一批，不是每个包各一份。 |
| `stringCount` | `0` | 生成的 string 数量，同样是全局一份 `values/strings.xml`。 |

包名和类名由随机小写字母加序号拼出来，同一配置重复构建时名字会变。不要在业务代码里引用这些生成类。

`resPrefix` 不要和现有资源前缀重叠，否则资源合并会因重名失败。建议每个马甲包用不同前缀，例如 `jk_`、`gd_`。

`packageBase` 不要和业务包名相同。用一个独立子包，例如 `com.example.app.junk`。

某个计数字段为 `0` 时，对应产物不生成。`packageCount = 0` 时没有 Java 和 Manifest 里的 Activity，但 `drawableCount`、`stringCount` 仍会生成资源。

### 4.3 生成物位置

变体 `debug` 的输出目录：

```text
app/build/generated/source/junk/debug/
├── java/                          生成的 Java
├── res/
│   ├── layout/                    每个 Activity 一个布局
│   ├── drawable/                  drawable XML
│   ├── values/strings.xml
│   └── raw/android_junk_code_keep.xml
├── AndroidManifest.xml            只包含生成的 <activity>
└── proguard-rules.pro             -keep class <生成包>.** { *; }
```

对应任务名是 `generateDebugJunkCode`、`generateReleaseJunkCode`。flavor 变体 `demoDebug` 的任务名是 `generateDemoDebugJunkCode`。

Java 源码和 `res` 会作为该变体的生成源码目录参与编译。Manifest 会合并进最终 Manifest。`proguard-rules.pro` 会加到该变体的 ProGuard 文件列表；只有开启压缩或混淆时这些 `-keep` 才会真正参与打包。`resPrefix` 非空时还会生成 `raw/android_junk_code_keep.xml`，用 `tools:keep` 保住带该前缀的 layout、drawable、string。`resPrefix` 为空时不生成这个 keep 文件。

生成的 Activity 继承 `android.app.Activity`，`onCreate` 里 `setContentView` 到对应布局。Manifest 条目没有 intent-filter，也没有 `android:exported`。

## 5. 多渠道

每个要生成代码的变体都要单独建一份配置。debug 和 release 可以数字不同。

```kotlin
androidJunkCode {
    variantConfig {
        create("demoDebug") {
            packageBase = "com.example.demo.junk"
            packageCount = 10
            activityCountPerPackage = 2
            otherCountPerPackage = 20
            methodCountPerClass = 10
            resPrefix = "demo_"
            drawableCount = 10
            stringCount = 10
        }
        create("fullRelease") {
            packageBase = "com.example.full.junk"
            packageCount = 40
            activityCountPerPackage = 4
            otherCountPerPackage = 80
            methodCountPerClass = 20
            resPrefix = "full_"
            drawableCount = 40
            stringCount = 40
        }
    }
}
```

变体名以 Android Studio 的 Build Variants 窗口，或 `./gradlew :app:tasks` 里的变体名为准。

## 6. 验证

同步工程后执行：

```bash
./gradlew :app:assembleDebug --console=plain
```

通过时能看到：

- 配置日志（仅 `debug = true`）：`AndroidJunkCode: generate code for variant debug`
- 任务 `:app:generateDebugJunkCode` 执行成功
- `app/build/generated/source/junk/debug/java` 下出现生成包
- `app/build/intermediates/merged_manifest/debug` 的合并 Manifest 里出现这些 Activity

Windows 用 `gradlew.bat`。

## 7. 常见错误

**Plugin garbage-code was not found**

`settings` 里没有 JitPack，或没有 `resolutionStrategy` 的 `useModule`。版本号少了 `v`，或写成了 `AndroidJunkCode` 而不是 `GarbageCode`，也会是这个错。

**Could not get unknown property / ApplicationAndroidComponentsExtension**

插件接到了 library 模块，或写在了 `com.android.application` 之前。

**某个变体没有生成代码**

`variantConfig` 的名字和变体名不一致。`Debug`、`debugRelease` 都不会匹配 `debug`。

**资源重复**

`resPrefix` 和工程里已有资源前缀相同，或两个变体意外写进了同一个源码集。每个变体使用自己的前缀。

**编译变慢、包体变大**

`packageCount * (activityCountPerPackage + otherCountPerPackage) * methodCountPerClass` 是方法规模的大致上限。先小后大。

**R8 把生成类删掉**

确认该变体开启了压缩，并且生成的 `proguard-rules.pro` 已经挂到变体上。默认规则是 `-keep class <生成包>.** { *; }`。资源侧依赖 `resPrefix` 非空时生成的 `tools:keep`。

## 8. 升级版本

JitPack 上已经构建成功的标签不会被覆盖。以后换版本时只改 catalog 里的版本号，例如 `v2.0.4`，`useModule` 那一行不用改。新标签需要先在 https://github.com/gumayosi/GarbageCode 上存在，并且 JitPack 构建成功后才能解析。
