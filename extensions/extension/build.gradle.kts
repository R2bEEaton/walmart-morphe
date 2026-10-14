extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.template.extension"
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
}
