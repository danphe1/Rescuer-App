plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
}

tasks.register<Exec>("startApi") {
    workingDir = rootProject.projectDir
    val os = org.gradle.internal.os.OperatingSystem.current()
    if (os.isWindows) {
        commandLine("powershell", "-Command", "Start-Process node -ArgumentList 'service' -WindowStyle Hidden")
    } else {
        commandLine("sh", "-c", "nohup node service >/dev/null 2>&1 &")
    }
}
