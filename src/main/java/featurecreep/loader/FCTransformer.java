package featurecreep.loader;

import java.nio.ByteBuffer;
import java.security.ProtectionDomain;

import org.jboss.modules.ClassTransformer;

public class FCTransformer implements ClassTransformer {

	public FCLoaderBasic loader;

	public FCTransformer(FCLoaderBasic loader) {
		this.loader = loader;
	}

	@Override
	public ByteBuffer transform(ClassLoader loader, String className, ProtectionDomain protectionDomain,
			ByteBuffer classBytes) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		ByteBuffer buff = classBytes;
		for (ClassTransformer transformer : this.loader.getTransformers()) {
			ByteBuffer changes = transformer.transform(loader, className, protectionDomain, classBytes);
			if (changes != null) {
				buff = changes;
			}

		}

		return buff;
	}

}

