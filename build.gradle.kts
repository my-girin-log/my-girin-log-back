plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.mygrinlog"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

extra["springAiVersion"] = "1.0.0-M3"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.ai:spring-ai-anthropic-spring-boot-starter")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 셸의 prod/MySQL 환경변수가 새어들어와 테스트가 외부 DB 를 잡으려 하는 사고 방지.
    // 테스트는 항상 application-test.yml (in-memory H2) 로만 돌아야 한다.
    systemProperty("spring.profiles.active", "test")
    environment("SPRING_PROFILES_ACTIVE", "test")
    environment("DB_URL", "")
    environment("DB_USER", "")
    environment("DB_PASSWORD", "")
    environment("ANTHROPIC_API_KEY", "dummy-key-disabled")
    environment("LLM_ENABLED", "false")
}
