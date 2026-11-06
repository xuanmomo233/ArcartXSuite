plugins { id("application") }
dependencies {
    implementation(project(":auth-engine"))
    implementation(project(":auth-protocol"))
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
application { mainClass.set("xuanmo.arcartxsuite.auth.center.AuthCenterMain") }
tasks.compileJava { options.encoding = "UTF-8"; options.release = 17 }
tasks.test { useJUnitPlatform() }
