package com.github.refactoringai.refactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;

import com.github.refactoringai.refactory.Model.Scope;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Predictor {

	@ConfigProperty(name = "MODELS_DIRECTORY", defaultValue = "models")
	Path modelsDir;

	@ConfigProperty(name = "PYTHON_PATH", defaultValue = "/usr/bin/python3")
	Path pythonPath;

	@ConfigProperty(name = "CI_PROJECT_DIR")
	File projectDir;

	@Inject
	Jsonb jsonb;

	/**
	 * Fetches the models in the {@link Predictor#modelsDir} And executes them one
	 * by one adding their results to a collection.
	 * 
	 * @param inputSamples The samples to feed to the models.
	 * @return The results of the prediction of all models.
	 * @throws IOException          When reading of models and their corresponding
	 *                              scripts fail or when the prediction script
	 *                              fails.
	 * @throws InterruptedException When the execution of the predict script fails.
	 */
	public Collection<Refactor> predict(Collection<Sample> inputSamples) throws IOException, InterruptedException {
		var models = fetchModels();
		var refactors = new ArrayList<Refactor>();
		for (var model : models) {
			refactors.addAll(model.execute(inputSamples));
		}
		return refactors;
	}

	/**
	 * Gets the directories from the {@link Predictor#modelsDir} and tries to build
	 * models for every directory
	 * 
	 * @return The models build
	 * @throws IOException When reading a model or property file fails
	 */
	private Collection<Model> fetchModels() throws IOException {
		List<Path> modelDirs;
		try (var pathStream = Files.list(modelsDir)) {
			modelDirs = pathStream.filter(Files::isDirectory).collect(Collectors.toList());
		}

		var models = new ArrayList<Model>();
		for (var modeldir : modelDirs) {
			models.add(createModel(modeldir));
		}
		return models;
	}

	private Model createModel(Path modelDir) throws IOException {
		var name = modelDir.getFileName().toString();

		final var joblibExtension = "joblib";
		var modelFile = fileName("model", name, joblibExtension);
		var scalerFile = fileName("scaler", name, joblibExtension);
		var featuresFile = fileName("features", name, "csv");
		var propertiesFile = fileName("properties", name, "json"); // Temporary solution

		var modelPath = modelDir.resolve(modelFile);
		var scalerPath = modelDir.resolve(scalerFile);
		var requiredFeatures = Files.readAllLines(modelDir.resolve(featuresFile));

		var modelProperties = jsonb.fromJson(Files.newInputStream(modelDir.resolve(propertiesFile)),
				ModelProperties.class);

		return new Model(pythonPath, modelPath, scalerPath, Scope.valueOf(modelProperties.getScope()),
				modelProperties.message, requiredFeatures);
	}

	private Path fileName(String prefix, String modelName, String extension) {
		return Paths.get(String.format("%s_%s.%s", prefix, modelName, extension));
	}

	// TODO will probably change this for a bit more defined solution, probably move
	// this together with the features csv, Also it is static public and not
	// private for jsonb
	public static class ModelProperties {
		private String message;
		private String scope;

		/**
		 * @return the message
		 */
		public String getMessage() {
			return message;
		}

		/**
		 * @param message the message to set
		 */
		public void setMessage(String message) {
			this.message = message;
		}

		/**
		 * @return the scope
		 */
		public String getScope() {
			return scope;
		}

		/**
		 * @param scope the scope to set
		 */
		public void setScope(String scope) {
			this.scope = scope;
		}

	}

}
