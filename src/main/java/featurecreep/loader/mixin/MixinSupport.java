package featurecreep.loader.mixin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host hook for applying mixin configs declared by FeatureCreep modules. The
 * loader never applies mixins itself; a host that runs Sponge Mixin supplies an
 * implementation through {@link featurecreep.loader.GameProvider#getMixinSupport()}.
 */
public interface MixinSupport {

    void registerMixinConfigs(String moduleName, List<String> configs, ClassLoader moduleClassLoader);

    MixinSupport NONE = new MixinSupport() {
        private final Set<String> warned = ConcurrentHashMap.newKeySet();

        @Override
        public void registerMixinConfigs(String moduleName, List<String> configs, ClassLoader moduleClassLoader) {
            if (configs == null || configs.isEmpty() || !warned.add(moduleName)) {
                return;
            }
            System.err.println("FeatureCreep module " + moduleName + " declares mixin configs " + configs
                    + " but this host does not apply mixins; they are ignored");
        }
    };
}
