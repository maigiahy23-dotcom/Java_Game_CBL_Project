plugins {
    application
    java
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

application {
    mainClass.set("com.cbl.game.app.App")
}

tasks.jar {
    manifest { attributes["Main-Class"] = "com.cbl.game.app.App" }
}
