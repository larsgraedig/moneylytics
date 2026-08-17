plugins {
    id("com.google.cloud.tools.jib")
}

jib {
    from {
        image = "eclipse-temurin:21-jre-alpine"
        platforms {
            platform {
                architecture = "arm64"
                os = "linux"
            }
            platform {
                architecture = "amd64"
                os = "linux"
            }
        }
    }

    to {
        val imageTag = System.getenv("COMMIT_HASH") ?: "latest"

        image = "docker.io/larsu/moneylytics:$imageTag"

        auth {
            username = System.getenv("DOCKERHUB_USERNAME")
            password = System.getenv("DOCKERHUB_PASSWORD")
        }
    }
}
