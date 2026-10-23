repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(project(":HospitalPlugin"))
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
}
