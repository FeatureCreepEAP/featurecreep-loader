package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.modules.ClassTransformer;
import org.junit.jupiter.api.Test;

class ClassFileTransformerBridgeTest {

    @Test
    void sliceOutputIsReturnedAsOnlyItsReadableBytes() throws Exception {
        ClassTransformer slicing = (loader, name, domain, bytes) -> {
            ByteBuffer big = ByteBuffer.wrap(new byte[] { 0, 1, 2, 3, 4 });
            big.position(1).limit(3);
            return big.slice();
        };
        ClassFileTransformer bridged = FCLoaderBasic.fromClassTransformer(slicing);

        assertArrayEquals(new byte[] { 1, 2 }, bridged.transform(null, "a/B", null, null, new byte[] { 9, 9, 9 }));
    }

    @Test
    void unchangedOutputReturnsTheInputArray() throws Exception {
        byte[] input = { 7, 8 };
        ClassTransformer passThrough = (loader, name, domain, bytes) -> bytes;
        ClassFileTransformer bridged = FCLoaderBasic.fromClassTransformer(passThrough);

        assertSame(input, bridged.transform(null, "a/B", null, null, input));
    }

    @Test
    void nullOutputReturnsTheInputArray() throws Exception {
        byte[] input = { 7 };
        ClassFileTransformer bridged = FCLoaderBasic.fromClassTransformer((loader, name, domain, bytes) -> null);

        assertSame(input, bridged.transform(null, "a/B", null, null, input));
    }

    @Test
    void removeTransformerRemovesTheRegisteredFileTransformer() {
        List<ClassTransformer> chain = new CopyOnWriteArrayList<>();
        FCInstrumentation instrumentation = new FCInstrumentation(loaderWithChain(chain));
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                return null;
            }
        };

        instrumentation.addTransformer(transformer);
        assertEquals(1, chain.size());

        assertTrue(instrumentation.removeTransformer(transformer));
        assertTrue(chain.isEmpty());
        assertFalse(instrumentation.removeTransformer(transformer));
    }

    private static FCLoaderBasic loaderWithChain(List<ClassTransformer> chain) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getTransformers" -> chain;
            case "addTransformer" -> InvocationHandler.invokeDefault(proxy, method, args);
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (FCLoaderBasic) Proxy.newProxyInstance(FCLoaderBasic.class.getClassLoader(),
                new Class<?>[] { FCLoaderBasic.class }, handler);
    }
}
