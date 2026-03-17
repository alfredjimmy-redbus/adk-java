#!/bin/bash
# Build ADK modules needed for Sarvam (avoids reactor build issues).
# Run from adk-java root: ./build-sarvam.sh

set -e
echo "Building core..."
mvn clean package install -Dmaven.test.skip=true -pl core -q

echo "Building dev..."
mvn install -Dmaven.test.skip=true -pl dev -q

echo "Building sarvam-ai..."
mvn install -Dmaven.test.skip=true -pl contrib/sarvam-ai -q

echo "Building maven-plugin..."
mvn install -Dmaven.test.skip=true -pl maven_plugin -q

echo "Done. ADK + Sarvam AI installed to ~/.m2/repository"
