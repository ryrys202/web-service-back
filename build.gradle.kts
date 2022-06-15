import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "io.github.kynmarsher"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // либа для читаемых uuid
    // конвертировать объекты в JSON и обратно
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.2.2")
    compileOnly("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("io.socket:socket.io-server:4.0.1")
    implementation("io.socket:engine.io-server:6.1.0")
    implementation("io.socket:engine.io-server-jetty:6.1.0")
    implementation("org.eclipse.jetty:jetty-server:9.4.19.v20190610")
    implementation("org.eclipse.jetty:jetty-servlet:9.4.19.v20190610")
    implementation("org.eclipse.jetty.websocket:websocket-server:9.4.46.v20220331")
    implementation("org.eclipse.jetty:jetty-servlets:9.4.19.v20190610")
    implementation("commons-io:commons-io:2.11.0")
    implementation("com.aventrix.jnanoid:jnanoid:2.0.0")
}

tasks.withType<ShadowJar> {
    manifest {
        attributes["Main-Class"] = "io.github.kynmarsher.webserviceback.Application"
    }
}