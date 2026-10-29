package featurecreep.loader;

import java.lang.instrument.ClassFileTransformer;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;

import org.jboss.modules.ClassTransformer;
import org.jboss.modules.JLIClassTransformer;

final class RemovableClassFileTransformer implements ClassTransformer {

	private final ClassFileTransformer delegate;
	private final JLIClassTransformer adapted;

	RemovableClassFileTransformer(ClassFileTransformer delegate) {
		this.delegate = delegate;
		this.adapted = new JLIClassTransformer(delegate);
	}

	ClassFileTransformer delegate() {
		return delegate;
	}

	@Override
	public ByteBuffer transform(ClassLoader loader, String className, ProtectionDomain protectionDomain,
			ByteBuffer classBytes) throws IllegalArgumentException {
		return adapted.transform(loader, className, protectionDomain, classBytes);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RemovableClassFileTransformer that && that.delegate == delegate;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(delegate);
	}
}
