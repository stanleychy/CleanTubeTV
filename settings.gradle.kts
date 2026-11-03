// Supply-chain policy:
// - Only Google Maven and Maven Central. No jitpack, no jcenter, no plugin portal.
// - Google Maven may only serve Google/AndroidX groups.
// - Projects may not declare their own repositories.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "CleanTubeTV"
include(":app")

// CodeQL (code scanning) restores the Gradle caches of the last run, which contain the local build cache.
// The CodeQL extractor sees only the code that the compiler compiles in its run. A compile task that comes
// from the build cache compiles nothing, and the analysis stops with "could not process any code".
// So the build cache is off while CodeQL runs the build. CodeQL sets CODEQL_* environment variables.
buildCache {
    local {
        isEnabled = System.getenv().keys.none { it.startsWith("CODEQL_") }
    }
}
