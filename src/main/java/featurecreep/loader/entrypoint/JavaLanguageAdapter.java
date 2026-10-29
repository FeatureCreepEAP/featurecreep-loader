package featurecreep.loader.entrypoint;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Built-in adapter. {@code main} calls {@code public static void main(String[])}; every other
 * type calls a {@code public static void} no-argument method named after the type.
 */
public final class JavaLanguageAdapter implements LanguageAdapter {

    @Override
    public String name() {
        return Entrypoint.JAVA_ADAPTER;
    }

    @Override
    public void invoke(Entrypoint entrypoint, ClassLoader moduleClassLoader, String[] args) throws Exception {
        Class<?> owner = Class.forName(entrypoint.className(), true, moduleClassLoader);
        boolean main = Entrypoint.MAIN.equals(entrypoint.type());
        Method method = main
                ? owner.getMethod("main", String[].class)
                : owner.getMethod(entrypoint.type());
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException(entrypoint.className() + "." + method.getName() + " must be static");
        }
        if (main) {
            method.invoke(null, (Object) args);
        } else {
            method.invoke(null);
        }
    }
}
