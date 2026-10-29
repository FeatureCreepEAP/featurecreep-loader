package featurecreep.loader.config;

/**
 * Immutable configuration metadata for one FeatureCreep module.
 *
 * <p>Only strings and the opaque {@link FCConfigScreenFactory} are stored, so the
 * entry is free of Minecraft or toolkit types. Entries are keyed by
 * {@link #getModId()}, the stable FeatureCreep module identifier.
 */
public final class FCConfigEntry {

    private final String modId;
    private final String displayName;
    private final String iconPath;
    private final FCConfigScreenFactory screenFactory;

    private FCConfigEntry(Builder builder) {
        this.modId = builder.modId;
        this.displayName = builder.displayName == null ? builder.modId : builder.displayName;
        this.iconPath = builder.iconPath;
        this.screenFactory = builder.screenFactory;
    }

    /** Starts a builder for the given module identifier. */
    public static Builder builder(String modId) {
        return new Builder(modId);
    }

    /** Stable FeatureCreep module identifier. */
    public String getModId() {
        return modId;
    }

    /** Human-readable name; defaults to the module identifier. */
    public String getDisplayName() {
        return displayName;
    }

    /** Optional icon resource path inside the module, or {@code null}. */
    public String getIconPath() {
        return iconPath;
    }

    /** Optional screen factory, or {@code null} when the module has no configuration screen. */
    public FCConfigScreenFactory getScreenFactory() {
        return screenFactory;
    }

    /** True when a configuration screen can be requested for this entry. */
    public boolean hasScreen() {
        return screenFactory != null;
    }

    @Override
    public String toString() {
        return "FCConfigEntry[" + modId + (hasScreen() ? ", screen" : "") + "]";
    }

    /** Builder for {@link FCConfigEntry}. */
    public static final class Builder {
        private final String modId;
        private String displayName;
        private String iconPath;
        private FCConfigScreenFactory screenFactory;

        private Builder(String modId) {
            String trimmed = modId == null ? "" : modId.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("A config entry needs a non-blank module id");
            }
            this.modId = trimmed;
        }

        public Builder displayName(String value) {
            this.displayName = blankToNull(value);
            return this;
        }

        public Builder iconPath(String value) {
            this.iconPath = blankToNull(value);
            return this;
        }

        public Builder screenFactory(FCConfigScreenFactory factory) {
            this.screenFactory = factory;
            return this;
        }

        public FCConfigEntry build() {
            return new FCConfigEntry(this);
        }

        private static String blankToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
