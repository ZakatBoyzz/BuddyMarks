name: Build BuddyMarks (Minecraft 1.21.4)

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build mod
        run: gradle --no-daemon clean build

      - name: Upload BuddyMarks JAR
        uses: actions/upload-artifact@v4
        with:
          name: BuddyMarks-Minecraft-1.21.4
          path: build/libs/buddymarks-*.jar
          if-no-files-found: error
          compression-level: 9
