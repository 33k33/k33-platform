plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    implementation(project(":libs::utils:config"))
    implementation(project(":libs::utils:logging"))
    implementation(Ktor.client.core)
    implementation(Ktor.client.cio)
    implementation(Ktor.client.logging)
    implementation(Ktor.client.contentNegotiation)
    implementation("io.ktor:ktor-serialization-jackson:_")

    // test
    testImplementation("io.kotest:kotest-runner-junit5-jvm:_")
}