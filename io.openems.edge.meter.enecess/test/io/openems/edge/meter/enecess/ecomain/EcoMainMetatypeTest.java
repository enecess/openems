package io.openems.edge.meter.enecess.ecomain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import io.openems.edge.meter.enecess.ecomain.branch.EcoMainBranchMeterType;
import io.openems.edge.meter.enecess.ecomain.core.EcoMainCoreImpl;

class EcoMainMetatypeTest {
	private static final String BUNDLE_SYMBOLIC_NAME = "io.openems.edge.meter.enecess";
	private static final String MAIN_METATYPE = "OSGI-INF/metatype/"
			+ "io.openems.edge.meter.enecess.ecomain.main.Config.xml";
	private static final String BRANCH_METATYPE = "OSGI-INF/metatype/"
			+ "io.openems.edge.meter.enecess.ecomain.branch.Config.xml";

	@Test
	void exposesOnlySupportedMeterTypesToGenericConfigurationUis() throws Exception {
		assertTrue(Arrays.stream(io.openems.edge.meter.enecess.ecomain.main.Config.class.getDeclaredMethods()) //
				.noneMatch(method -> method.getName().equals("type")),
				"Main meter type must be fixed to GRID and hidden from configuration");

		var branchType = io.openems.edge.meter.enecess.ecomain.branch.Config.class.getDeclaredMethod("type");
		assertEquals(EcoMainBranchMeterType.class, branchType.getReturnType());
		assertEquals(EcoMainBranchMeterType.CONSUMPTION_METERED, branchType.getDefaultValue());
		assertEquals(List.of(//
				"PRODUCTION", //
				"PRODUCTION_AND_CONSUMPTION", //
				"CONSUMPTION_METERED", //
				"CONSUMPTION_NOT_METERED"), //
				Arrays.stream(EcoMainBranchMeterType.values()).map(Enum::name).toList());

		var generatedBundle = generatedBundle();
		if (generatedBundle.isPresent()) {
			try (var bundle = new JarFile(generatedBundle.get().toFile())) {
				assertTrue(attributeDefinitions(bundle, MAIN_METATYPE, "type").isEmpty(),
						"Main meter type must be fixed to GRID and hidden from generated MetaType");

				var generatedBranchType = attributeDefinitions(bundle, BRANCH_METATYPE, "type").getFirst();
				assertEquals("CONSUMPTION_METERED", generatedBranchType.getAttribute("default"));
				assertEquals(Arrays.stream(EcoMainBranchMeterType.values()).map(Enum::name).toList(), //
						optionValues(generatedBranchType));
			}
		}
	}

	private static Optional<Path> generatedBundle() throws Exception {
		var codeSource = EcoMainCoreImpl.class.getProtectionDomain().getCodeSource();
		assertNotNull(codeSource, "Missing production bundle CodeSource");
		var outputDirectory = Path.of(codeSource.getLocation().toURI());
		assertTrue(Files.isDirectory(outputDirectory), "Expected Bnd classes directory");
		var projectDirectory = outputDirectory.getParent();
		assertNotNull(projectDirectory, "Missing production project directory");
		var bundle = projectDirectory.resolve("generated").resolve(BUNDLE_SYMBOLIC_NAME + ".jar");
		if (Files.notExists(bundle)) {
			return Optional.empty();
		}
		assertTrue(Files.isRegularFile(bundle), "Generated bundle path is not a file");
		return Optional.of(bundle);
	}

	private static List<Element> attributeDefinitions(JarFile bundle, String metatypePath, String id)
			throws Exception {
		var entry = bundle.getJarEntry(metatypePath);
		assertNotNull(entry, "Missing generated MetaType descriptor " + metatypePath);
		try (InputStream input = bundle.getInputStream(entry)) {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			var definitions = factory.newDocumentBuilder().parse(input).getElementsByTagName("AD");
			var result = new ArrayList<Element>();
			for (var index = 0; index < definitions.getLength(); index++) {
				var definition = (Element) definitions.item(index);
				if (id.equals(definition.getAttribute("id"))) {
					result.add(definition);
				}
			}
			return List.copyOf(result);
		}
	}

	private static List<String> optionValues(Element attributeDefinition) {
		var options = attributeDefinition.getElementsByTagName("Option");
		var result = new ArrayList<String>();
		for (var index = 0; index < options.getLength(); index++) {
			result.add(((Element) options.item(index)).getAttribute("value"));
		}
		return List.copyOf(result);
	}
}
