package featurecreep.loader.environment;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Interfaces;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.jboss.modules.ClassTransformer;

import featurecreep.loader.ExecutionSide;

/**
 * Removes Fabric {@code @Environment} members and {@code @EnvironmentInterface} interfaces for the other side. Class-level {@code @Environment} is not enforced yet.
 */
public final class EnvironmentStripper implements ClassTransformer {

	private static final ClassFile CLASS_FILE = ClassFile.of(ClassFile.StackMapsOption.STACK_MAPS_WHEN_REQUIRED);

	// Matches the Environment, EnvironmentInterface and EnvironmentInterfaces descriptors alike.
	private static final byte[] MARKER = "net/fabricmc/api/Environment".getBytes(StandardCharsets.UTF_8);

	private static final ClassDesc ENVIRONMENT = ClassDesc.of("net.fabricmc.api.Environment");
	private static final ClassDesc ENVIRONMENT_INTERFACE = ClassDesc.of("net.fabricmc.api.EnvironmentInterface");
	private static final ClassDesc ENVIRONMENT_INTERFACES = ClassDesc.of("net.fabricmc.api.EnvironmentInterfaces");

	private static final String CLIENT = "CLIENT";
	private static final String SERVER = "SERVER";

	private final String runningSide;

	public EnvironmentStripper(ExecutionSide side) {
		if (side == ExecutionSide.CLIENT) {
			this.runningSide = CLIENT;
		} else if (side == ExecutionSide.SERVER) {
			this.runningSide = SERVER;
		} else {
			throw new IllegalArgumentException("Unknown execution side: " + side);
		}
	}

	@Override
	public ByteBuffer transform(ClassLoader loader, String className, ProtectionDomain protectionDomain,
			ByteBuffer classBytes) throws IllegalArgumentException {
		if (!containsMarker(classBytes)) {
			return null;
		}

		byte[] bytes = new byte[classBytes.remaining()];
		classBytes.get(classBytes.position(), bytes);
		ClassModel model = CLASS_FILE.parse(bytes);

		Set<ClassDesc> foreignInterfaces = foreignInterfaces(model);
		boolean stripInterfaces = model.interfaces().stream().anyMatch(itf -> foreignInterfaces.contains(itf.asSymbol()));
		boolean stripMembers = model.elementStream().anyMatch(this::stripsMember);
		if (!stripInterfaces && !stripMembers) {
			return null;
		}

		// transformClass copies the original interface list, and the Interfaces element would restore it, so both are bypassed.
		byte[] out = CLASS_FILE.build(model.thisClass().asSymbol(), builder -> {
			builder.withFlags(model.flags().flagsMask());
			model.superclass().ifPresent(builder::withSuperclass);
			builder.withVersion(model.majorVersion(), model.minorVersion());
			builder.withInterfaces(model.interfaces().stream()
					.filter(itf -> !foreignInterfaces.contains(itf.asSymbol())).toList());
			for (ClassElement element : model.elementList()) {
				if (!(element instanceof Interfaces) && !stripsMember(element)) {
					builder.with(element);
				}
			}
		});
		return ByteBuffer.wrap(out);
	}

	private static boolean containsMarker(ByteBuffer buf) {
		int last = buf.limit() - MARKER.length;
		outer:
		for (int i = buf.position(); i <= last; i++) {
			if (buf.get(i) != MARKER[0]) {
				continue;
			}
			for (int j = 1; j < MARKER.length; j++) {
				if (buf.get(i + j) != MARKER[j]) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

	private boolean stripsMember(ClassElement element) {
		if (element instanceof FieldModel field) {
			return hasForeignEnvironment(field.findAttribute(Attributes.runtimeVisibleAnnotations()));
		}
		if (element instanceof MethodModel method) {
			return hasForeignEnvironment(method.findAttribute(Attributes.runtimeVisibleAnnotations()));
		}
		return false;
	}

	private boolean hasForeignEnvironment(Optional<RuntimeVisibleAnnotationsAttribute> attribute) {
		if (attribute.isEmpty()) {
			return false;
		}
		for (Annotation annotation : attribute.get().annotations()) {
			if (ENVIRONMENT.equals(annotation.classSymbol())) {
				return isForeign(enumConstant(annotation, "value"));
			}
		}
		return false;
	}

	private Set<ClassDesc> foreignInterfaces(ClassModel model) {
		Set<ClassDesc> result = new HashSet<>();
		Optional<RuntimeVisibleAnnotationsAttribute> attribute = model
				.findAttribute(Attributes.runtimeVisibleAnnotations());
		if (attribute.isEmpty()) {
			return result;
		}
		for (Annotation annotation : attribute.get().annotations()) {
			if (ENVIRONMENT_INTERFACE.equals(annotation.classSymbol())) {
				collectForeignInterface(annotation, result);
			} else if (ENVIRONMENT_INTERFACES.equals(annotation.classSymbol())) {
				if (elementValue(annotation, "value") instanceof AnnotationValue.OfArray array) {
					for (AnnotationValue value : array.values()) {
						if (value instanceof AnnotationValue.OfAnnotation nested) {
							collectForeignInterface(nested.annotation(), result);
						}
					}
				}
			}
		}
		return result;
	}

	private void collectForeignInterface(Annotation annotation, Set<ClassDesc> result) {
		String side = enumConstant(annotation, "value");
		if (!isForeign(side)) {
			return;
		}
		if (elementValue(annotation, "itf") instanceof AnnotationValue.OfClass itf) {
			result.add(itf.classSymbol());
		}
	}

	private boolean isForeign(String side) {
		return (CLIENT.equals(side) || SERVER.equals(side)) && !side.equals(runningSide);
	}

	private static String enumConstant(Annotation annotation, String name) {
		return elementValue(annotation, name) instanceof AnnotationValue.OfEnum constant
				? constant.constantName().stringValue()
				: null;
	}

	private static AnnotationValue elementValue(Annotation annotation, String name) {
		for (AnnotationElement element : annotation.elements()) {
			if (element.name().stringValue().equals(name)) {
				return element.value();
			}
		}
		return null;
	}
}
