package featurecreep.loader.environment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import featurecreep.loader.ExecutionSide;

class EnvironmentStripperTest {

	private static final String ENV_TYPE = """
			package net.fabricmc.api;
			public enum EnvType { CLIENT, SERVER }
			""";

	private static final String ENVIRONMENT = """
			package net.fabricmc.api;
			import java.lang.annotation.*;
			@Retention(RetentionPolicy.RUNTIME)
			@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
			public @interface Environment { EnvType value(); }
			""";

	private static final String ENVIRONMENT_INTERFACE = """
			package net.fabricmc.api;
			import java.lang.annotation.*;
			@Repeatable(EnvironmentInterfaces.class)
			@Retention(RetentionPolicy.RUNTIME)
			@Target(ElementType.TYPE)
			public @interface EnvironmentInterface { EnvType value(); Class<?> itf(); }
			""";

	private static final String ENVIRONMENT_INTERFACES = """
			package net.fabricmc.api;
			import java.lang.annotation.*;
			@Retention(RetentionPolicy.RUNTIME)
			@Target(ElementType.TYPE)
			public @interface EnvironmentInterfaces { EnvironmentInterface[] value(); }
			""";

	private static final String SAMPLE = """
			package fix;
			import net.fabricmc.api.*;
			@Environment(EnvType.CLIENT)
			@EnvironmentInterface(value = EnvType.SERVER, itf = Runnable.class)
			public class Sample implements Runnable, java.io.Serializable {
			    @Environment(EnvType.CLIENT) public int clientField;
			    @Environment(EnvType.SERVER) public int serverField;
			    public int plainField;
			    @Environment(EnvType.CLIENT) public void clientMethod() {}
			    @Environment(EnvType.SERVER) public void serverMethod() {}
			    public void plainMethod() {}
			    public void run() {}
			}
			""";

	private static final String MULTI = """
			package fix;
			import net.fabricmc.api.*;
			@EnvironmentInterfaces({
			    @EnvironmentInterface(value = EnvType.CLIENT, itf = Runnable.class),
			    @EnvironmentInterface(value = EnvType.SERVER, itf = java.io.Serializable.class)
			})
			public class Multi implements Runnable, java.io.Serializable {
			    public void run() {}
			}
			""";

	private static final String CLIENT_ONLY = """
			package fix;
			import net.fabricmc.api.*;
			public class ClientOnly {
			    @Environment(EnvType.CLIENT) public void clientMethod() {}
			    public void plainMethod() {}
			}
			""";

	private static final String PLAIN = """
			package fix;
			public class Plain implements Runnable {
			    public int value;
			    public void run() {}
			}
			""";

	private static final Map<String, byte[]> CLASSES = new HashMap<>();

	@BeforeAll
	static void compileFixtures() throws IOException {
		Path src = Files.createTempDirectory("env-fixtures-src");
		Path out = Files.createTempDirectory("env-fixtures-out");
		Map<String, String> sources = Map.of(
				"net/fabricmc/api/EnvType.java", ENV_TYPE,
				"net/fabricmc/api/Environment.java", ENVIRONMENT,
				"net/fabricmc/api/EnvironmentInterface.java", ENVIRONMENT_INTERFACE,
				"net/fabricmc/api/EnvironmentInterfaces.java", ENVIRONMENT_INTERFACES,
				"fix/Sample.java", SAMPLE,
				"fix/Multi.java", MULTI,
				"fix/ClientOnly.java", CLIENT_ONLY,
				"fix/Plain.java", PLAIN);
		String[] args = new String[sources.size() + 2];
		args[0] = "-d";
		args[1] = out.toString();
		int i = 2;
		for (Map.Entry<String, String> source : sources.entrySet()) {
			Path file = src.resolve(source.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
			args[i++] = file.toString();
		}
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int status = ToolProvider.getSystemJavaCompiler().run(null, null, err, args);
		assertEquals(0, status, err.toString(StandardCharsets.UTF_8));
		for (String name : List.of("fix/Sample", "fix/Multi", "fix/ClientOnly", "fix/Plain")) {
			CLASSES.put(name, Files.readAllBytes(out.resolve(name + ".class")));
		}
	}

	@Test
	void clientSideStripsServerOnlyMembersAndInterface() {
		ClassModel model = strip(ExecutionSide.CLIENT, "fix/Sample");

		assertEquals(Set.of("clientField", "plainField"), fieldNames(model));
		assertEquals(Set.of("<init>", "clientMethod", "plainMethod", "run"), methodNames(model));
		assertEquals(Set.of("java/io/Serializable"), interfaceNames(model));
	}

	@Test
	void serverSideStripsClientOnlyMembersAndKeepsSharedInterface() {
		ClassModel model = strip(ExecutionSide.SERVER, "fix/Sample");

		assertEquals(Set.of("serverField", "plainField"), fieldNames(model));
		assertEquals(Set.of("<init>", "serverMethod", "plainMethod", "run"), methodNames(model));
		assertEquals(Set.of("java/lang/Runnable", "java/io/Serializable"), interfaceNames(model));
	}

	@Test
	void classLevelEnvironmentIsNotEnforced() {
		ClassModel model = strip(ExecutionSide.SERVER, "fix/Sample");

		RuntimeVisibleAnnotationsAttribute annotations = model
				.findAttribute(Attributes.runtimeVisibleAnnotations()).orElseThrow();
		assertTrue(annotations.annotations().stream()
				.anyMatch(a -> a.classSymbol().descriptorString().equals("Lnet/fabricmc/api/Environment;")));
	}

	@Test
	void containerInterfacesAreStrippedPerSide() {
		assertEquals(Set.of("java/lang/Runnable"), interfaceNames(strip(ExecutionSide.CLIENT, "fix/Multi")));
		assertEquals(Set.of("java/io/Serializable"), interfaceNames(strip(ExecutionSide.SERVER, "fix/Multi")));
	}

	@Test
	void classWithoutRemovableMembersReturnsNull() {
		assertNull(transform(ExecutionSide.CLIENT, ByteBuffer.wrap(CLASSES.get("fix/Plain"))));
		assertNull(transform(ExecutionSide.CLIENT, ByteBuffer.wrap(CLASSES.get("fix/ClientOnly"))));
	}

	@Test
	void nonZeroPositionReadsOnlyReadableRegion() {
		byte[] sample = CLASSES.get("fix/Sample");
		ByteBuffer padded = ByteBuffer.allocate(5 + sample.length + 3);
		padded.put(new byte[5]).put(sample).put(new byte[3]);
		padded.position(5).limit(5 + sample.length);

		ByteBuffer out = transform(ExecutionSide.CLIENT, padded);

		assertEquals(5, padded.position());
		assertEquals(5 + sample.length, padded.limit());
		assertArrayEquals(bytesOf(transform(ExecutionSide.CLIENT, ByteBuffer.wrap(sample))), bytesOf(out));
	}

	private static ClassModel strip(ExecutionSide side, String name) {
		ByteBuffer out = transform(side, ByteBuffer.wrap(CLASSES.get(name)));
		assertNotNull(out, "expected stripped output for " + name);
		return ClassFile.of().parse(bytesOf(out));
	}

	private static ByteBuffer transform(ExecutionSide side, ByteBuffer input) {
		return new EnvironmentStripper(side).transform(null, "fix/x", null, input);
	}

	private static byte[] bytesOf(ByteBuffer buf) {
		byte[] bytes = new byte[buf.remaining()];
		buf.duplicate().get(bytes);
		return bytes;
	}

	private static Set<String> fieldNames(ClassModel model) {
		return model.fields().stream().map(f -> f.fieldName().stringValue()).collect(Collectors.toSet());
	}

	private static Set<String> methodNames(ClassModel model) {
		return model.methods().stream().map(m -> m.methodName().stringValue()).collect(Collectors.toSet());
	}

	private static Set<String> interfaceNames(ClassModel model) {
		return model.interfaces().stream().map(i -> i.asInternalName()).collect(Collectors.toSet());
	}
}
