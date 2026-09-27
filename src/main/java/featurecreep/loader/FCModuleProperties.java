package featurecreep.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.Module;

/**
 * FeatureCreep module metadata keys.  All of these may be declared as normal
 * JBoss Modules properties in {@code module.xml}.  Manifest attributes with the
 * same names are also imported for backwards compatibility.
 */
public final class FCModuleProperties {

    private FCModuleProperties() {
    }

    public static final String MOD_ID = "FeatureCreep-Mod-Id";
    public static final String DISPLAY_NAME = "FeatureCreep-Name";
    public static final String DESCRIPTION = "FeatureCreep-Description";
    public static final String AUTHORS = "FeatureCreep-Authors";
    public static final String WEBSITE = "FeatureCreep-Website";
    public static final String LICENSE = "FeatureCreep-License";

    /** public (default) or isolated. */
    public static final String VISIBILITY = "FeatureCreep-Visibility";
    /** true by default for public modules. */
    public static final String AUTO_DEPENDENCIES = "FeatureCreep-Auto-Dependencies";
    /** true by default; allows game/host class-loader fallback. */
    public static final String HOST_FALLBACK = "FeatureCreep-Host-Fallback";
    /** both (default), client, or server. */
    public static final String SIDE = "FeatureCreep-Side";
    /** Overrides whether FeatureCreep calls the module's main class. */
    public static final String RUNNABLE = "FeatureCreep-Runnable";
    /** private (default), public, or disabled. */
    public static final String NESTED_JARS = "FeatureCreep-Nested-Jars";
    /** Optional comma-separated extra nested JAR paths. */
    public static final String NESTED_JAR_PATHS = "FeatureCreep-Nested-Jar-Paths";

    public static final String MIXIN_CONFIGS = "MixinConfigs";

    /**
     * Marker written by FPMBuild SuperInjection. A JAR with this entry remains
     * FeatureCreep-owned even though compatibility metadata makes it look like a
     * Fabric/Forge/NeoForge/Sponge mod to the surrounding superloader.
     */
    public static final String SUPERINJECTION_OWNERSHIP_MARKER = "META-INF/featurecreep/not-superloader-owned";
    public static final String SPONGE_MIXIN_CONFIG = "SpongeMixinConfig";
    public static final String FC_MIXIN_CONFIGS = "FeatureCreep-Mixin-Configs";
    /** Comma/space separated java.lang.instrument.ClassFileTransformer or JBoss ClassTransformer implementations. */
    public static final String TRANSFORMER_CLASSES = "FeatureCreep-Transformer-Classes";
    /** Legacy/short alias accepted for migrated flat mods. */
    public static final String TRANSFORMER_CLASS = "Transformer-Class";
    /** module (JBoss-only), game (Instrumentation), or all (default). */
    public static final String TRANSFORMER_SCOPE = "FeatureCreep-Transformer-Scope";

    public static final String PREMAIN_CLASS = "Premain-Class";
    public static final String AGENT_CLASS = "Agent-Class";
    public static final String EARLY_LISTENER_CLASS = "EarlyListener-Class";
    public static final String CAN_REDEFINE_CLASSES = "Can-Redefine-Classes";
    public static final String CAN_RETRANSFORM_CLASSES = "Can-Retransform-Classes";
    public static final String MAIN_CLASS = "Main-Class";
    public static final String MODULE_VERSION = "Module-Version";

    public static final String SOURCE = "FeatureCreep-Source";
    public static final String NESTED_OWNER = "FeatureCreep-Nested-Owner";

    public static String get(Map<String, String> properties, String key) {
        if (properties == null || key == null) {
            return null;
        }
        String direct = properties.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static boolean getBoolean(Map<String, String> properties, String key, boolean defaultValue) {
        String value = get(properties, key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    public static boolean isPublic(Map<String, String> properties, boolean nested) {
        String value = get(properties, VISIBILITY);
        if (value == null || value.trim().isEmpty()) {
            return !nested;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !(normalized.equals("isolated") || normalized.equals("private") || normalized.equals("hidden"));
    }

    public static boolean autoDependencies(Map<String, String> properties, boolean publicModule) {
        return publicModule && getBoolean(properties, AUTO_DEPENDENCIES, true);
    }

    public static boolean hostFallback(Map<String, String> properties) {
        return getBoolean(properties, HOST_FALLBACK, true);
    }

    public static boolean isRunnable(Map<String, String> properties, boolean defaultValue) {
        return getBoolean(properties, RUNNABLE, defaultValue);
    }

    public static String nestedJarMode(Map<String, String> properties) {
        String value = get(properties, NESTED_JARS);
        return value == null ? "private" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean supportsSide(Map<String, String> properties, ExecutionSide side) {
        String value = get(properties, SIDE);
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("both") || value.equalsIgnoreCase("any")) {
            return true;
        }
        if (side == null) {
            return true;
        }
        if (side == ExecutionSide.CLIENT) {
            return value.equalsIgnoreCase("client");
        }
        if (side == ExecutionSide.SERVER) {
            return value.equalsIgnoreCase("server");
        }
        return true;
    }

    public static List<String> splitList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> unique = new LinkedHashSet<String>();
        for (String item : value.split("[,;\\s]+")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return new ArrayList<String>(unique);
    }

    public static List<String> getMixinConfigs(Map<String, String> properties) {
        Set<String> configs = new LinkedHashSet<String>();
        configs.addAll(splitList(get(properties, MIXIN_CONFIGS)));
        configs.addAll(splitList(get(properties, SPONGE_MIXIN_CONFIG)));
        configs.addAll(splitList(get(properties, FC_MIXIN_CONFIGS)));
        return new ArrayList<String>(configs);
    }

    public static List<String> getMixinConfigs(Module module) {
        if (module == null) {
            return Collections.emptyList();
        }
        Set<String> configs = new LinkedHashSet<String>();
        configs.addAll(splitList(module.getProperty(MIXIN_CONFIGS)));
        configs.addAll(splitList(module.getProperty(SPONGE_MIXIN_CONFIG)));
        configs.addAll(splitList(module.getProperty(FC_MIXIN_CONFIGS)));
        return new ArrayList<String>(configs);
    }
    public static List<String> getTransformerClasses(Map<String, String> properties) {
        Set<String> classes = new LinkedHashSet<String>();
        classes.addAll(splitList(get(properties, TRANSFORMER_CLASSES)));
        classes.addAll(splitList(get(properties, TRANSFORMER_CLASS)));
        classes.addAll(splitList(get(properties, "transformerclass")));
        return new ArrayList<String>(classes);
    }

    public static List<String> getTransformerClasses(Module module) {
        if (module == null) return Collections.emptyList();
        Set<String> classes = new LinkedHashSet<String>();
        classes.addAll(splitList(module.getProperty(TRANSFORMER_CLASSES)));
        classes.addAll(splitList(module.getProperty(TRANSFORMER_CLASS)));
        classes.addAll(splitList(module.getProperty("transformerclass")));
        return new ArrayList<String>(classes);
    }

}
