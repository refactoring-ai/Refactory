# Refactory

This project implements the recomendation of method level refactoring opportunities using Machine Learning on GitLab merge requests.
The research that lead to this project consisted of recommending ''extract method'' on merge requests.
Currently the tool is limited to the following toolchain as the research was performed in this setting:
* GitLab
* Survalyzer
* Extract method
It should not be too dificult to adapt this tool to other toolchains however.

# Setup

First you will need a model in ONNX format.
Please see the repo for [data collection](https://github.com/refactoring-ai/Data-Collection)
 for instructions on how to collect data and the repo for [machine learning](https://github.com/refactoring-ai/Machine-Learning)
 for instructions on how to generate an ONNX model from this data.

After this set up a postgres DB to store your results.
Then setup your environment variables by either renaming `.env-example` to `.env` or setting the corresponding environment variables.


## Running the application in dev mode

You can run your application in dev mode that enables live coding using: `./gradlew quarkusDev`

## Packaging and running the application

The application can be packaged using `./gradlew quarkusBuild`.
It produces the `refactory-0.1.0-runner.jar` file in the `build` directory.

The application is now runnable using `java -jar build/refactory-0.1.0-runner.jar`.

