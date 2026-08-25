package io.openems.edge.meter.enecess.ecomain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.osgi.service.event.EventHandler;
import org.w3c.dom.Element;

import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.enecess.ecomain.api.EcoMainCore;
import io.openems.edge.meter.enecess.ecomain.core.EcoMainCoreImpl;
import io.openems.edge.meter.enecess.ecomain.branch.MeterEcoMainBranch;
import io.openems.edge.meter.enecess.ecomain.branch.MeterEcoMainBranchImpl;
import io.openems.edge.meter.enecess.ecomain.main.MeterEcoMainMain;
import io.openems.edge.meter.enecess.ecomain.main.MeterEcoMainMainImpl;
import io.openems.edge.meter.api.ElectricityMeter;

class EcoMainArchitectureTest {

	private static final String BUNDLE_SYMBOLIC_NAME = "io.openems.edge.meter.enecess";
	private static final String COMPONENT_ANNOTATION_DESCRIPTOR = "Lorg/osgi/service/component/annotations/Component;";
	private static final Set<String> LEGACY_COMPONENT_PIDS = Set.of(//
			"EnEcess.EcoMain.Core", //
			"Meter.EnEcess.EcoMain.Main", //
			"Meter.EnEcess.EcoMain.Branch");
	private static final List<ComponentExpectation> COMPONENTS = List.of(//
			new ComponentExpectation("Meter.enecess.ecoMain.Core", EcoMainCoreImpl.class), //
			new ComponentExpectation("Meter.enecess.ecoMain.Main", MeterEcoMainMainImpl.class), //
			new ComponentExpectation("Meter.enecess.ecoMain.Branch", MeterEcoMainBranchImpl.class));

	@Test
	void bundleMetadataExposesExpectedSymbolicNameAndFactoryPids() throws Exception {
		assertEquals(Set.copyOf(COMPONENTS), Set.copyOf(componentInventory()));
	}

	@Test
	void coreIsTheOnlyModbusComponentInTheBundleInventory() throws Exception {
		var modbusComponents = componentInventory().stream() //
				.map(ComponentExpectation::implementation) //
				.filter(ModbusComponent.class::isAssignableFrom) //
				.toList();

		assertEquals(List.of(EcoMainCoreImpl.class), modbusComponents);
	}

	@Test
	void logicalMetersAddNoBusinessNatureBeyondElectricityMeter() {
		assertEquals(Set.of(ElectricityMeter.class, OpenemsComponent.class, EventHandler.class), //
				Set.of(MeterEcoMainMain.class.getInterfaces()));
		assertEquals(Set.of(ElectricityMeter.class, OpenemsComponent.class, EventHandler.class), //
				Set.of(MeterEcoMainBranch.class.getInterfaces()));
		assertEquals(Set.of(MeterEcoMainMain.class, ElectricityMeter.class, OpenemsComponent.class, EventHandler.class), //
				Set.of(MeterEcoMainMainImpl.class.getInterfaces()));
		assertEquals(Set.of(MeterEcoMainBranch.class, ElectricityMeter.class, OpenemsComponent.class, EventHandler.class), //
				Set.of(MeterEcoMainBranchImpl.class.getInterfaces()));
	}

	@Test
	void componentImplementationsAndBusinessInterfacesAddOnlyIdentityBusinessChannels() throws Exception {
		assertEquals(Set.of(//
				"MODEL", //
				"SERIAL_NUMBER", //
				"SOFTWARE_VERSION", //
				"WRONG_DEVICE_MODEL", //
				"UNSUPPORTED_SOFTWARE_VERSION", //
				"SERIAL_NUMBER_MISMATCH"), //
				Arrays.stream(EcoMainCore.ChannelId.values()).map(Enum::name).collect(Collectors.toSet()));
		assertEquals(Set.of(EcoMainCore.ChannelId.class), declaredChannelTypes(EcoMainCore.class));
		for (var type : componentImplementationsAndBusinessInterfaces()) {
			if (!type.equals(EcoMainCore.class)) {
				assertTrue(declaredChannelTypes(type).isEmpty(), () -> type.getName() + " declares business channels");
			}
		}
		assertTrue(Arrays.stream(ElectricityMeter.ChannelId.values()) //
				.map(Enum::name) //
				.noneMatch(name -> name.contains("POWER_FACTOR") || name.contains("COS_PHI")
						|| name.startsWith("SET_") || name.contains("CONTROL")));
	}

	@Test
	void actualProtocolUsesOnlyFc3AndOmitsUnsupportedRegisterSurfaces() {
		var tasks = new TestableEcoMainCore().protocol().getTaskManager().getTasks();

		var coveredAddresses = tasks.stream() //
				.map(task -> assertInstanceOf(FC3ReadRegistersTask.class, task)) //
				.flatMapToInt(task -> IntStream.range(task.getStartAddress(), task.getStartAddress() + task.getLength())) //
				.boxed() //
				.collect(Collectors.toSet());
		assertTrue(coveredAddresses.containsAll(Set.of(0, 1328, 3000, 3009)));
		assertTrue(IntStream.rangeClosed(2000, 2039).noneMatch(coveredAddresses::contains));
		assertTrue(IntStream.rangeClosed(3010, 3103).noneMatch(coveredAddresses::contains));
	}

	private static List<ComponentExpectation> componentInventory() throws Exception {
		var codeSource = EcoMainCoreImpl.class.getProtectionDomain().getCodeSource();
		assertNotNull(codeSource, "Missing production bundle CodeSource");
		var bundlePath = Path.of(codeSource.getLocation().toURI());
		if (Files.isDirectory(bundlePath)) {
			var compiledComponents = compiledComponentInventory(bundlePath);
			var generatedBundle = generatedBundlePath(bundlePath);
			if (generatedBundle.isPresent()) {
				assertEquals(Set.copyOf(compiledComponents), Set.copyOf(componentDescriptors(generatedBundle.get())), //
						"Generated bundle component inventory differs from compiled classes");
			}
			return compiledComponents;
		}
		return componentDescriptors(bundlePath);
	}

	private static Optional<Path> generatedBundlePath(Path outputDirectory) throws IOException {
		assertEquals("bin", outputDirectory.getFileName().toString(), "Unexpected Bnd classes directory");
		var projectDirectory = outputDirectory.getParent();
		assertNotNull(projectDirectory, "Missing production project directory");
		assertEquals(BUNDLE_SYMBOLIC_NAME, projectDirectory.getFileName().toString());
		var expectedBundle = projectDirectory.resolve("generated").resolve(BUNDLE_SYMBOLIC_NAME + ".jar") //
				.toAbsolutePath().normalize();
		var buildFiles = projectDirectory.resolve("generated/buildfiles");
		if (Files.notExists(buildFiles) && Files.notExists(expectedBundle)) {
			return Optional.empty();
		}
		assertTrue(Files.isRegularFile(buildFiles), "Missing standard Bnd generated/buildfiles inventory");
		assertTrue(Files.isRegularFile(expectedBundle), "Generated bundle path is not a file");
		var declaredBundles = Files.readAllLines(buildFiles).stream() //
				.map(String::strip) //
				.filter(line -> !line.isEmpty()) //
				.map(line -> Path.of(line)) //
				.map(path -> path.isAbsolute() ? path : projectDirectory.resolve(path)) //
				.map(path -> path.toAbsolutePath().normalize()) //
				.toList();
		assertEquals(List.of(expectedBundle), declaredBundles, "Unexpected Bnd build output inventory");
		return Optional.of(expectedBundle);
	}

	private static List<ComponentExpectation> compiledComponentInventory(Path outputDirectory) throws Exception {
		var result = new ArrayList<ComponentExpectation>();
		var componentPids = new HashSet<String>();
		try (var paths = Files.walk(outputDirectory)) {
			for (var classFile : paths.filter(Files::isRegularFile) //
					.filter(path -> path.getFileName().toString().endsWith(".class")) //
					.sorted() //
					.toList()) {
				var className = className(outputDirectory, classFile);
				assertTrue(className.startsWith(BUNDLE_SYMBOLIC_NAME + "."), //
						() -> "Unexpected production class outside bundle package: " + className);
				var metadata = readClassFile(classFile);
				var legacyMetadata = metadata.constantPool().utf8Values().stream() //
						.filter(EcoMainArchitectureTest::isLegacyMetadata) //
						.toList();
				assertTrue(legacyMetadata.isEmpty(), () -> className + " contains legacy metadata " + legacyMetadata);
				var componentPid = componentPid(metadata, classFile);
				if (componentPid.isEmpty()) {
					continue;
				}
				assertTrue(componentPids.add(componentPid.get()), //
						() -> "Duplicate @Component name " + componentPid.get());
				var implementation = Class.forName(className, false, EcoMainCoreImpl.class.getClassLoader());
				result.add(new ComponentExpectation(componentPid.get(), implementation));
			}
		}
		return List.copyOf(result);
	}

	private static String className(Path outputDirectory, Path classFile) {
		var relativeName = outputDirectory.relativize(classFile).toString();
		return relativeName.substring(0, relativeName.length() - ".class".length()) //
				.replace(outputDirectory.getFileSystem().getSeparator(), ".");
	}

	private static List<ComponentExpectation> componentDescriptors(Path bundlePath) throws Exception {
		var result = new ArrayList<ComponentExpectation>();
		var descriptorPaths = new HashSet<String>();
		var componentPids = new HashSet<String>();
		try (var bundle = new JarFile(bundlePath.toFile())) {
			var manifest = bundle.getManifest();
			assertNotNull(manifest, "Missing production bundle manifest");
			var symbolicName = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
			assertNotNull(symbolicName, "Missing Bundle-SymbolicName manifest header");
			assertEquals(BUNDLE_SYMBOLIC_NAME, symbolicName.split(";", 2)[0]);
			var serviceComponent = manifest.getMainAttributes().getValue("Service-Component");
			assertNotNull(serviceComponent, "Missing Service-Component manifest header");
			var documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilderFactory.setNamespaceAware(true);
			var documentBuilder = documentBuilderFactory.newDocumentBuilder();
			for (var descriptorClause : serviceComponent.split(",")) {
				var descriptorPath = descriptorClause.strip().split(";", 2)[0];
				assertTrue(!descriptorPath.isBlank(), "Blank Service-Component descriptor path");
				assertTrue(descriptorPaths.add(descriptorPath), "Duplicate DS descriptor " + descriptorPath);
				var descriptorEntry = bundle.getJarEntry(descriptorPath);
				assertNotNull(descriptorEntry, "Missing generated DS descriptor " + descriptorPath);
				try (InputStream descriptor = bundle.getInputStream(descriptorEntry)) {
					var component = documentBuilder.parse(descriptor).getDocumentElement();
					assertEquals("component", component.getLocalName(), "Unexpected DS document root");
					var componentPid = component.getAttribute("name");
					assertTrue(!componentPid.isBlank(), "Unnamed DS component in " + descriptorPath);
					assertTrue(componentPids.add(componentPid), "Duplicate DS component PID " + componentPid);
					assertTrue(!isLegacyMetadata(componentPid), "Legacy DS component PID " + componentPid);
					var implementations = component.getElementsByTagNameNS("*", "implementation");
					assertEquals(1, implementations.getLength(), "Unexpected DS implementation count");
					var implementation = (Element) implementations.item(0);
					var implementationName = implementation.getAttribute("class");
					assertTrue(!implementationName.isBlank(), "Unnamed DS implementation in " + descriptorPath);
					assertTrue(!isLegacyMetadata(implementationName), "Legacy DS implementation " + implementationName);
					var implementationClass = Class.forName(implementationName, false,
							EcoMainCoreImpl.class.getClassLoader());
					result.add(new ComponentExpectation(componentPid, implementationClass));
				}
			}
		}
		return List.copyOf(result);
	}

	private static ClassFileMetadata readClassFile(Path classFile) throws IOException {
		try (var input = new DataInputStream(Files.newInputStream(classFile))) {
			if (input.readInt() != 0xCAFEBABE) {
				throw new IOException("Invalid class-file header in " + classFile);
			}
			input.skipNBytes(4); // minor_version and major_version
			var constantPool = readConstantPool(input, classFile);
			input.skipNBytes(6); // access_flags, this_class and super_class
			input.skipNBytes(2L * input.readUnsignedShort()); // interfaces
			skipMembers(input, constantPool); // fields
			skipMembers(input, constantPool); // methods

			var invisibleAnnotations = new ArrayList<ClassAnnotation>();
			var visibleAnnotations = new ArrayList<ClassAnnotation>();
			var seenInvisibleAnnotations = false;
			var seenVisibleAnnotations = false;
			var attributeCount = input.readUnsignedShort();
			for (var index = 0; index < attributeCount; index++) {
				var attributeName = constantPool.utf8(input.readUnsignedShort(), "class attribute name");
				var attributeLength = Integer.toUnsignedLong(input.readInt());
				if (attributeName.equals("RuntimeInvisibleAnnotations")) {
					if (seenInvisibleAnnotations) {
						throw new IOException("Duplicate RuntimeInvisibleAnnotations in " + classFile);
					}
					seenInvisibleAnnotations = true;
					invisibleAnnotations.addAll(readAnnotations(attributeBytes(input, attributeLength, classFile), //
							constantPool, classFile));
				} else if (attributeName.equals("RuntimeVisibleAnnotations")) {
					if (seenVisibleAnnotations) {
						throw new IOException("Duplicate RuntimeVisibleAnnotations in " + classFile);
					}
					seenVisibleAnnotations = true;
					visibleAnnotations.addAll(readAnnotations(attributeBytes(input, attributeLength, classFile), //
							constantPool, classFile));
				} else {
					input.skipNBytes(attributeLength);
				}
			}
			if (input.read() != -1) {
				throw new IOException("Trailing bytes after class attributes in " + classFile);
			}
			return new ClassFileMetadata(constantPool, List.copyOf(invisibleAnnotations), //
					List.copyOf(visibleAnnotations));
		}
	}

	private static ConstantPool readConstantPool(DataInputStream input, Path classFile) throws IOException {
		var constantPoolCount = input.readUnsignedShort();
		var tags = new int[constantPoolCount];
		var values = new Object[constantPoolCount];
		for (var index = 1; index < constantPoolCount; index++) {
			var tag = input.readUnsignedByte();
			tags[index] = tag;
			switch (tag) {
			case 1 -> values[index] = input.readUTF();
			case 3, 4 -> input.skipNBytes(4);
			case 5, 6 -> {
				input.skipNBytes(8);
				index++;
			}
			case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
			case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
			case 15 -> input.skipNBytes(3);
			default -> throw new IOException("Unsupported constant-pool tag " + tag + " in " + classFile);
			}
		}
		return new ConstantPool(classFile, tags, values);
	}

	private static void skipMembers(DataInputStream input, ConstantPool constantPool) throws IOException {
		var memberCount = input.readUnsignedShort();
		for (var memberIndex = 0; memberIndex < memberCount; memberIndex++) {
			input.skipNBytes(2); // access_flags
			constantPool.utf8(input.readUnsignedShort(), "member name");
			constantPool.utf8(input.readUnsignedShort(), "member descriptor");
			var attributeCount = input.readUnsignedShort();
			for (var attributeIndex = 0; attributeIndex < attributeCount; attributeIndex++) {
				constantPool.utf8(input.readUnsignedShort(), "member attribute name");
				input.skipNBytes(Integer.toUnsignedLong(input.readInt()));
			}
		}
	}

	private static DataInputStream attributeBytes(DataInputStream input, long length, Path classFile)
			throws IOException {
		if (length > Integer.MAX_VALUE) {
			throw new IOException("Oversized annotation attribute in " + classFile);
		}
		var bytes = input.readNBytes((int) length);
		if (bytes.length != length) {
			throw new EOFException("Truncated annotation attribute in " + classFile);
		}
		return new DataInputStream(new ByteArrayInputStream(bytes));
	}

	private static List<ClassAnnotation> readAnnotations(DataInputStream input, ConstantPool constantPool,
			Path classFile) throws IOException {
		try (input) {
			var result = new ArrayList<ClassAnnotation>();
			var annotationCount = input.readUnsignedShort();
			for (var index = 0; index < annotationCount; index++) {
				result.add(readAnnotation(input, constantPool, classFile));
			}
			if (input.read() != -1) {
				throw new IOException("Trailing RuntimeAnnotations data in " + classFile);
			}
			return List.copyOf(result);
		}
	}

	private static ClassAnnotation readAnnotation(DataInputStream input, ConstantPool constantPool, Path classFile)
			throws IOException {
		var descriptor = constantPool.utf8(input.readUnsignedShort(), "annotation descriptor");
		if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
			throw new IOException("Malformed annotation descriptor " + descriptor + " in " + classFile);
		}
		var elements = new ArrayList<AnnotationElement>();
		var elementNames = new HashSet<String>();
		var elementCount = input.readUnsignedShort();
		for (var index = 0; index < elementCount; index++) {
			var name = constantPool.utf8(input.readUnsignedShort(), "annotation element name");
			if (!elementNames.add(name)) {
				throw new IOException("Duplicate annotation element " + name + " in " + classFile);
			}
			elements.add(new AnnotationElement(name, readAnnotationValue(input, constantPool, classFile)));
		}
		return new ClassAnnotation(descriptor, List.copyOf(elements));
	}

	private static AnnotationValue readAnnotationValue(DataInputStream input, ConstantPool constantPool,
			Path classFile) throws IOException {
		var tag = (char) input.readUnsignedByte();
		return switch (tag) {
		case 'B', 'C', 'I', 'S', 'Z' -> {
			constantPool.requireTag(input.readUnsignedShort(), 3, "integer annotation value");
			yield new AnnotationValue(tag, null);
		}
		case 'D' -> {
			constantPool.requireTag(input.readUnsignedShort(), 6, "double annotation value");
			yield new AnnotationValue(tag, null);
		}
		case 'F' -> {
			constantPool.requireTag(input.readUnsignedShort(), 4, "float annotation value");
			yield new AnnotationValue(tag, null);
		}
		case 'J' -> {
			constantPool.requireTag(input.readUnsignedShort(), 5, "long annotation value");
			yield new AnnotationValue(tag, null);
		}
		case 's' -> new AnnotationValue(tag, //
				constantPool.utf8(input.readUnsignedShort(), "string annotation value"));
		case 'e' -> {
			constantPool.utf8(input.readUnsignedShort(), "enum annotation type");
			constantPool.utf8(input.readUnsignedShort(), "enum annotation value");
			yield new AnnotationValue(tag, null);
		}
		case 'c' -> {
			constantPool.utf8(input.readUnsignedShort(), "class annotation value");
			yield new AnnotationValue(tag, null);
		}
		case '@' -> {
			readAnnotation(input, constantPool, classFile);
			yield new AnnotationValue(tag, null);
		}
		case '[' -> {
			var valueCount = input.readUnsignedShort();
			for (var index = 0; index < valueCount; index++) {
				readAnnotationValue(input, constantPool, classFile);
			}
			yield new AnnotationValue(tag, null);
		}
		default -> throw new IOException("Unsupported annotation value tag " + tag + " in " + classFile);
		};
	}

	private static Optional<String> componentPid(ClassFileMetadata metadata, Path classFile) throws IOException {
		var visibleComponents = metadata.visibleAnnotations().stream() //
				.filter(annotation -> annotation.descriptor().equals(COMPONENT_ANNOTATION_DESCRIPTOR)) //
				.toList();
		if (!visibleComponents.isEmpty()) {
			throw new IOException("@Component must use RuntimeInvisibleAnnotations in " + classFile);
		}
		var components = metadata.invisibleAnnotations().stream() //
				.filter(annotation -> annotation.descriptor().equals(COMPONENT_ANNOTATION_DESCRIPTOR)) //
				.toList();
		if (components.isEmpty()) {
			return Optional.empty();
		}
		if (components.size() != 1) {
			throw new IOException("Duplicate @Component annotations in " + classFile);
		}
		var nameElements = components.getFirst().elements().stream() //
				.filter(element -> element.name().equals("name")) //
				.toList();
		if (nameElements.size() != 1) {
			throw new IOException("@Component must declare exactly one name element in " + classFile);
		}
		var nameValue = nameElements.getFirst().value();
		if (nameValue.tag() != 's' || nameValue.stringValue() == null || nameValue.stringValue().isBlank()) {
			throw new IOException("@Component name must be a non-empty string in " + classFile);
		}
		if (isLegacyMetadata(nameValue.stringValue())) {
			throw new IOException("Legacy @Component name " + nameValue.stringValue() + " in " + classFile);
		}
		return Optional.of(nameValue.stringValue());
	}

	private static boolean isLegacyMetadata(String value) {
		return LEGACY_COMPONENT_PIDS.contains(value) //
				|| value.contains("io.openems.edge.enecess.ecomain") //
				|| value.contains("io/openems/edge/enecess/ecomain");
	}

	private static Set<Class<?>> componentImplementationsAndBusinessInterfaces() throws Exception {
		var result = new HashSet<Class<?>>();
		for (var descriptor : componentInventory()) {
			var implementation = descriptor.implementation();
			result.add(implementation);
			var pending = new ArrayDeque<Class<?>>();
			for (var type = implementation; type != null; type = type.getSuperclass()) {
				pending.addAll(Arrays.asList(type.getInterfaces()));
			}
			while (!pending.isEmpty()) {
				var businessInterface = pending.removeFirst();
				pending.addAll(Arrays.asList(businessInterface.getInterfaces()));
				if (isFromProductionBundle(businessInterface)) {
					result.add(businessInterface);
				}
			}
		}
		return Set.copyOf(result);
	}

	private static boolean isFromProductionBundle(Class<?> type) {
		var typeCodeSource = type.getProtectionDomain().getCodeSource();
		var bundleCodeSource = EcoMainCoreImpl.class.getProtectionDomain().getCodeSource();
		return typeCodeSource != null && bundleCodeSource != null
				&& typeCodeSource.getLocation().equals(bundleCodeSource.getLocation());
	}

	private static Set<Class<?>> declaredChannelTypes(Class<?> type) {
		return Arrays.stream(type.getDeclaredClasses()) //
				.filter(io.openems.edge.common.channel.ChannelId.class::isAssignableFrom) //
				.collect(Collectors.toSet());
	}

	private record ComponentExpectation(String pid, Class<?> implementation) {
	}

	private record ClassFileMetadata(ConstantPool constantPool, List<ClassAnnotation> invisibleAnnotations,
			List<ClassAnnotation> visibleAnnotations) {
	}

	private record ClassAnnotation(String descriptor, List<AnnotationElement> elements) {
	}

	private record AnnotationElement(String name, AnnotationValue value) {
	}

	private record AnnotationValue(char tag, String stringValue) {
	}

	private record ConstantPool(Path classFile, int[] tags, Object[] values) {

		private String utf8(int index, String role) throws IOException {
			this.requireTag(index, 1, role);
			return (String) this.values[index];
		}

		private void requireTag(int index, int expectedTag, String role) throws IOException {
			if (index <= 0 || index >= this.tags.length || this.tags[index] != expectedTag) {
				throw new IOException("Invalid " + role + " constant-pool index " + index + " in " + this.classFile);
			}
		}

		private Set<String> utf8Values() {
			var result = new HashSet<String>();
			for (var index = 1; index < this.tags.length; index++) {
				if (this.tags[index] == 1) {
					result.add((String) this.values[index]);
				}
			}
			return Set.copyOf(result);
		}
	}

	private static final class TestableEcoMainCore extends EcoMainCoreImpl {

		private ModbusProtocol protocol() {
			return super.defineModbusProtocol();
		}
	}
}
