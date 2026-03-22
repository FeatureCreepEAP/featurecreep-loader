package featurecreep.loader.flat;

import java.io.File;
import java.util.Properties;

public class FlatModMetadata {
    private final String modId;
    private final String mainClass;
    private final String transformerClass;
    private final String spongeMixinConfig;
    private final File sourceFile;

    public FlatModMetadata(Properties props, File sourceFile) {
        this.modId = props.getProperty("modid", "unknown");
        this.mainClass = props.getProperty("mainclass");
        this.transformerClass = props.getProperty("transformerclass");
        this.spongeMixinConfig = props.getProperty("spongemixinconfig");
        this.sourceFile = sourceFile;
    }

    public String getModId() { return modId; }
    public String getMainClass() { return mainClass; }
    public String getTransformerClass() { return transformerClass; }
    public String getSpongeMixinConfig() { return spongeMixinConfig; }
    public File getSourceFile() { return sourceFile; }
}