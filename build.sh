./gradlew quarkusBuild --uber-jar -xtest
rm refactory-0.1.0-runner.jar
cp build/refactory-0.1.0-runner.jar .