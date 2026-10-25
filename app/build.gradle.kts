import java.util.Properties

val appConfig = Properties().apply {
    file("../app-config.properties").inputStream().use { load(it) }
}
val configuredPackage = appConfig.getProperty("package_name", "com.example.websiteapp").trim()
val configuredAppName = appConfig.getProperty("app_name", "Website App").trim()
val configuredHomeUrl = appConfig.getProperty("home_url", "https://example.com/").trim()

plugins { id("com.android.application") }

android {
    namespace = "com.websitewrapper.app"
    compileSdk = 34
    defaultConfig {
        applicationId = configuredPackage
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "HOME_URL", "\"${configuredHomeUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        resValue("string", "app_name", configuredAppName.replace("\\", "\\\\").replace("\"", "\\\""))
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
