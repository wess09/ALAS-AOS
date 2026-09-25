plugins {
    id("azurpilot.android.application")
    id("azurpilot.android.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.aliothmoon.azurpilot"

    sourceSets {
        named("main") {
            // proot 九件套（libproot/libproot-loader/libtalloc/busybox/shim 等，Spike A 钉版产物）；
            // 与 src/main/jniLibs/（上游框架的拉取件，gitignore）分开放，本目录是构建输入要入库
            jniLibs.srcDir("src/main/prootLibs")
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/native/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    androidResources {
        // rootfs.tar.xz 已压缩且要按字节读进度（assets.openFd 只对未压缩资产生效）
        noCompress += "zip"
        noCompress += "xz"
    }
}

val verifyBundledAzurPilotRuntime = tasks.register("verifyBundledAzurPilotRuntime") {
    val archive = layout.projectDirectory.file("src/main/assets/rootfs/rootfs.tar.xz")
    val manifest = layout.projectDirectory.file("src/main/assets/rootfs/BUILD_MANIFEST")
    doLast {
        check(archive.asFile.isFile && archive.asFile.length() > 0) {
            "缺少 AzurPilot ARM64 rootfs.tar.xz；先运行 rootfs workflow 并复制构建产物"
        }
        check(manifest.asFile.isFile && manifest.asFile.readText().contains("\"runtime\": \"azurpilot-android\"")) {
            "缺少与 AzurPilot rootfs 配套的 BUILD_MANIFEST"
        }
    }
}

tasks.matching { it.name.startsWith("package") || it.name.startsWith("assemble") }
    .configureEach { dependsOn(verifyBundledAzurPilotRuntime) }

dependencies {
    compileOnly(project(":hidden-api"))

    implementation(project(":annotation-api"))
    ksp(project(":ksp-processor"))

    // MIUI 上系统权限页的跳转差异大，自己拼 Intent 覆盖不全
    implementation(libs.xx.permissions)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.libsu)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.window)
    implementation(libs.androidx.browser)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)

    // 首启解压 rootfs.tar.xz（设备端流式解 tar/xz；busybox tar 解 ubuntu 硬链接有前向引用死坑）
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)
    // 前台模式控制层：拖拽/吸边/多屏/返回键拦截都在库里，自己写这几样是纯坑区
    implementation(libs.floatingx)
    implementation(libs.floatingx.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
