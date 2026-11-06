plugins { id("java-library") }
dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.0.0")
}
tasks.compileJava { options.encoding = "UTF-8"; options.release = 17 }
tasks.test { useJUnitPlatform() }
