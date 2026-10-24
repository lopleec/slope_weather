import java.util.Properties
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }

val localRelease=providers.gradleProperty("localRelease").orNull=="true"
val signingFile=rootProject.file(".signing/signing.properties")
val releaseSigning=Properties().apply {if(signingFile.isFile) signingFile.inputStream().use {load(it)}}
fun signingValue(property: String,environment: String)=
    providers.environmentVariable(environment).orNull ?: releaseSigning.getProperty(property)
val releaseStorePath=signingValue("storeFile","SLOPE_RELEASE_STORE_FILE")
val releaseStorePassword=signingValue("storePassword","SLOPE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias=signingValue("keyAlias","SLOPE_RELEASE_KEY_ALIAS")
val releaseKeyPassword=signingValue("keyPassword","SLOPE_RELEASE_KEY_PASSWORD")
val releaseStoreFile=releaseStorePath?.let {path->File(path).let {if(it.isAbsolute) it else File(signingFile.parentFile,path)}}
val signingValues=listOf(releaseStorePath,releaseStorePassword,releaseKeyAlias,releaseKeyPassword)
val productionSigningReady=signingValues.all {!it.isNullOrBlank()}
if(signingValues.any {!it.isNullOrBlank()} && !productionSigningReady) {
    throw GradleException("Release signing requires all four SLOPE_RELEASE_* values or a complete .signing/signing.properties file.")
}
android {
    namespace = "com.lopleec.slopeweather"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.lopleec.slopeweather"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.1.19"
        testInstrumentationRunner = "com.lopleec.slopeweather.ui.WeatherRenderChecks"
    }
    bundle { language { enableSplit = false } }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if(productionSigningReady) create("production") {
            storeFile=releaseStoreFile
            storePassword=releaseStorePassword
            keyAlias=releaseKeyAlias
            keyPassword=releaseKeyPassword
        }
    }
    buildTypes { release {
        isMinifyEnabled = true
        // Optional local optimized build, compatible with the existing development install.
        if(localRelease) signingConfig=signingConfigs.getByName("debug")
        else if(productionSigningReady) signingConfig=signingConfigs.getByName("production")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.shredzone.commons:commons-suncalc:3.11")
    implementation("org.jsoup:jsoup:1.23.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

apply(from = rootProject.file("tools/runtime-notices.gradle"))

tasks.register("verifyReleaseSigning") {
    group="verification"
    description="Reject unsigned or debug-signed public release builds."
    doLast {
        check(!localRelease) {"Public releases cannot use -PlocalRelease=true."}
        check(productionSigningReady && releaseStoreFile?.isFile==true) {
            "Configure a private release keystore using .signing/signing.properties or SLOPE_RELEASE_* environment variables."
        }
        val keys=KeyStore.getInstance(releaseStoreFile!!,releaseStorePassword!!.toCharArray())
        check(keys.isKeyEntry(releaseKeyAlias)) {"The configured release alias must contain a private key."}
        check(keys.getKey(releaseKeyAlias,releaseKeyPassword!!.toCharArray())!=null)
        val certificate=keys.getCertificate(releaseKeyAlias) as X509Certificate
        certificate.checkValidity()
        check(!certificate.subjectX500Principal.name.contains("Android Debug",ignoreCase=true)) {"A debug certificate cannot sign a public release."}
        logger.lifecycle("Release signing certificate verified.")
    }
}
tasks.register("checkRelease") {
    group="verification"
    description="Build and verify a production-signed release candidate."
    dependsOn("verifyReleaseSigning","testReleaseUnitTest","lintRelease","assembleRelease")
}
