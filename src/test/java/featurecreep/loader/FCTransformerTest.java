package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jboss.modules.ClassTransformer;
import org.junit.jupiter.api.Test;

class FCTransformerTest {

    @Test
    void laterTransformersReceiveEarlierOutput() {
        ByteBuffer original = ByteBuffer.wrap(new byte[] { 9 });
        ByteBuffer rewritten = ByteBuffer.wrap(new byte[] { 1, 2 });
        List<ByteBuffer> seenByLater = new ArrayList<>();

        ClassTransformer first = (loader, name, domain, bytes) -> rewritten;
        ClassTransformer second = (loader, name, domain, bytes) -> {
            seenByLater.add(bytes);
            return null;
        };

        ByteBuffer result = transform(original, first, second);

        assertEquals(rewritten, seenByLater.get(0));
        assertEquals(rewritten, result);
    }

    @Test
    void nullOutputKeepsPreviousBytes() {
        ByteBuffer original = ByteBuffer.wrap(new byte[] { 9 });
        ClassTransformer none = (loader, name, domain, bytes) -> null;

        assertEquals(original, transform(original, none));
    }

    private static ByteBuffer transform(ByteBuffer input, ClassTransformer... chain) {
        List<ClassTransformer> list = List.of(chain);
        FCLoaderBasic loader = (FCLoaderBasic) Proxy.newProxyInstance(
                FCLoaderBasic.class.getClassLoader(),
                new Class<?>[] { FCLoaderBasic.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getTransformers")) return list;
                    throw new UnsupportedOperationException(method.getName());
                });
        return new FCTransformer(loader).transform(null, "a/B", null, input);
    }
}
