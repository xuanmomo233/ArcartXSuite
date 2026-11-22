plugins { id("java") }
dependencies {
    implementation(project(":auth-protocol"))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
tasks.compileJava { options.encoding = "UTF-8"; options.release = 17 }
tasks.test { useJUnitPlatform() }
