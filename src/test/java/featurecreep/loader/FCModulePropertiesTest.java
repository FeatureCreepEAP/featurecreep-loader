package featurecreep.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FCModulePropertiesTest {

    @Test
    void propertyLookupIsCaseInsensitive() {
        Map<String, String> properties = new LinkedHashMap<String, String>();
        properties.put("featurecreep-visibility", "isolated");
        assertEquals("isolated", FCModuleProperties.get(properties, FCModuleProperties.VISIBILITY));
    }

    @Test
    void topLevelModulesArePublicAndNestedModulesPrivateByDefault() {
        Map<String, String> properties = new LinkedHashMap<String, String>();
        assertTrue(FCModuleProperties.isPublic(properties, false));
        assertFalse(FCModuleProperties.isPublic(properties, true));
    }

    @Test
    void explicitVisibilityOverridesDefaults() {
        Map<String, String> properties = new LinkedHashMap<String, String>();
        properties.put(FCModuleProperties.VISIBILITY, "public");
        assertTrue(FCModuleProperties.isPublic(properties, true));
        properties.put(FCModuleProperties.VISIBILITY, "isolated");
        assertFalse(FCModuleProperties.isPublic(properties, false));
    }

    @Test
    void mixinConfigurationsAreMergedAndDeduplicated() {
        Map<String, String> properties = new LinkedHashMap<String, String>();
        properties.put(FCModuleProperties.MIXIN_CONFIGS, "a.mixins.json,b.mixins.json");
        properties.put(FCModuleProperties.SPONGE_MIXIN_CONFIG, "b.mixins.json c.mixins.json");
        properties.put(FCModuleProperties.FC_MIXIN_CONFIGS, "d.mixins.json");

        List<String> configs = FCModuleProperties.getMixinConfigs(properties);
        assertEquals(Arrays.asList("a.mixins.json", "b.mixins.json", "c.mixins.json", "d.mixins.json"), configs);
    }

    @Test
    void sideFilteringMatchesClientAndServer() {
        Map<String, String> properties = new LinkedHashMap<String, String>();
        properties.put(FCModuleProperties.SIDE, "client");
        assertTrue(FCModuleProperties.supportsSide(properties, ExecutionSide.CLIENT));
        assertFalse(FCModuleProperties.supportsSide(properties, ExecutionSide.SERVER));
        properties.put(FCModuleProperties.SIDE, "both");
        assertTrue(FCModuleProperties.supportsSide(properties, ExecutionSide.CLIENT));
        assertTrue(FCModuleProperties.supportsSide(properties, ExecutionSide.SERVER));
    }

    @Test
    void splitListAcceptsCommaSemicolonAndWhitespace() {
        assertEquals(Arrays.asList("a", "b", "c", "d"), FCModuleProperties.splitList("a,b; c  d,a"));
    }
}
