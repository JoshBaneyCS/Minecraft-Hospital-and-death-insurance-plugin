subprojects {
    apply(plugin = "java")

    group = rootProject.property("group") as String
    version = rootProject.property("version") as String

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
        "testImplementation"("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.named<ProcessResources>("processResources") {
        filesMatching("plugin.yml") { expand("version" to project.version) }
    }
}
