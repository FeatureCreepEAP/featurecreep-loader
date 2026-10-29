package featurecreep.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import featurecreep.loader.entrypoint.Entrypoint;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Small, Java-25-friendly reader for the parts of module.xml FeatureCreep needs
 * while constructing a JBoss {@code ModuleSpec}.  It deliberately uses only
 * public JBoss Modules APIs; no reflective access to ConcreteModuleSpec is
 * required.
 */
public final class FCModuleDescriptor {

    public static final String[] MODULE_XML_LOCATIONS = { "modules/module.xml", "module.xml" };

    /**
     * How a dependency affects loading. {@code breaks} is intentionally absent: a mod must not be able to crash another.
     */
    public enum DependencyKind {
        REQUIRED, OPTIONAL, RECOMMENDS, SUGGESTS, CONFLICTS;

        public static DependencyKind parse(String value) throws IOException {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "required" -> REQUIRED;
                case "optional" -> OPTIONAL;
                case "recommends" -> RECOMMENDS;
                case "suggests" -> SUGGESTS;
                case "conflicts" -> CONFLICTS;
                default -> throw new IOException("Unknown dependency kind '" + value + "'");
            };
        }
    }

    public static final class Dependency {
        private final String name;
        private final DependencyKind kind;
        private final boolean export;
        private final boolean services;
        private final String versionRange;

        Dependency(String name, boolean optional, boolean export, boolean services) {
            this(name, optional, export, services, null);
        }

        Dependency(String name, boolean optional, boolean export, boolean services, String versionRange) {
            this(name, optional ? DependencyKind.OPTIONAL : DependencyKind.REQUIRED, export, services, versionRange);
        }

        Dependency(String name, DependencyKind kind, boolean export, boolean services, String versionRange) {
            this.name = name;
            this.kind = kind;
            this.export = export;
            this.services = services;
            this.versionRange = versionRange;
        }

        public String getName() { return name; }
        public DependencyKind getKind() { return kind; }

        /**
         * True when loading does not require this dependency: optional, recommends, or suggests.
         */
        public boolean isOptional() {
            return kind == DependencyKind.OPTIONAL || kind == DependencyKind.RECOMMENDS || kind == DependencyKind.SUGGESTS;
        }

        public boolean isConflict() { return kind == DependencyKind.CONFLICTS; }
        public boolean isExport() { return export; }
        public boolean isServices() { return services; }

        /**
         * Optional version range the dependency must satisfy, from the
         * {@code version} or {@code version-range} attribute; {@code null} when omitted.
         */
        public String getVersionRange() { return versionRange; }
    }

    private String declaredName;
    private String mainClass;
    private String manifestMainClass;
    private String version;
    private String xmlLocation;
    private final Map<String, String> properties = new LinkedHashMap<String, String>();
    private final List<Dependency> dependencies = new ArrayList<Dependency>();
    private final List<String> resourceRoots = new ArrayList<String>();
    private final Map<String, List<String>> providedServices = new LinkedHashMap<String, List<String>>();
    private final List<Entrypoint> declaredEntrypoints = new ArrayList<Entrypoint>();

    public String getDeclaredName() { return declaredName; }
    public String getMainClass() { return mainClass; }
    public String getVersion() { return version; }
    public String getXmlLocation() { return xmlLocation; }
    public Map<String, String> getProperties() { return Collections.unmodifiableMap(properties); }
    public List<Dependency> getDependencies() { return Collections.unmodifiableList(dependencies); }

    /**
     * Entrypoints in declaration order. {@code main-class} or the manifest Main-Class comes first as a {@code main} entrypoint.
     */
    public List<Entrypoint> getEntrypoints() {
        List<Entrypoint> all = new ArrayList<Entrypoint>();
        if (mainClass != null && !mainClass.isEmpty()) {
            all.add(new Entrypoint(Entrypoint.MAIN, mainClass, Entrypoint.JAVA_ADAPTER));
        }
        for (Entrypoint entrypoint : declaredEntrypoints) {
            if (!(Entrypoint.MAIN.equals(entrypoint.type()) && entrypoint.className().equals(mainClass))) {
                all.add(entrypoint);
            }
        }
        return Collections.unmodifiableList(all);
    }
    public List<String> getResourceRoots() { return Collections.unmodifiableList(resourceRoots); }
    public Map<String, List<String>> getProvidedServices() { return Collections.unmodifiableMap(providedServices); }

    public String getProperty(String key) {
        return FCModuleProperties.get(properties, key);
    }

    public boolean hasModuleXml() {
        return xmlLocation != null;
    }

    public static FCModuleDescriptor read(ResourceLoader loader) throws IOException {
        FCModuleDescriptor result = new FCModuleDescriptor();
        result.importManifest(loader);

        for (String location : MODULE_XML_LOCATIONS) {
            Resource resource = loader.getResource(location);
            if (resource != null) {
                result.xmlLocation = location;
                try (InputStream in = resource.openStream()) {
                    result.parseXml(in);
                }
                break;
            }
        }
        if (!result.hasModuleXml()) {
            result.useManifestMainClass();
        }
        return result;
    }

    private void useManifestMainClass() {
        mainClass = manifestMainClass;
        if (mainClass != null && !mainClass.isEmpty()) {
            properties.put(FCModuleProperties.MAIN_CLASS, mainClass);
        }
    }

    private void importManifest(ResourceLoader loader) throws IOException {
        Resource manifestResource = loader.getResource("META-INF/MANIFEST.MF");
        if (manifestResource == null) {
            return;
        }
        Manifest manifest = new Manifest();
        try (InputStream in = manifestResource.openStream()) {
            manifest.read(in);
        }
        Attributes attrs = manifest.getMainAttributes();
        for (Map.Entry<Object, Object> entry : attrs.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !Attributes.Name.MAIN_CLASS.equals(entry.getKey())) {
                properties.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        manifestMainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);
        version = attrs.getValue(FCModuleProperties.MODULE_VERSION);
        if (declaredName == null) {
            declaredName = attrs.getValue("Automatic-Module-Name");
        }
    }

    private void parseXml(InputStream input) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            harden(factory);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(input);
            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }

            String rootName = attr(root, "name");
            if (!rootName.isEmpty()) {
                declaredName = rootName;
            }
            String rootVersion = attr(root, "version");
            if (!rootVersion.isEmpty()) {
                version = rootVersion;
            }

            for (Element child : childElements(root)) {
                String name = localName(child);
                if ("main-class".equals(name)) {
                    String value = attr(child, "name");
                    if (!value.isEmpty()) {
                        mainClass = value;
                    }
                } else if ("properties".equals(name)) {
                    parseProperties(child);
                } else if ("dependencies".equals(name)) {
                    parseDependencies(child);
                } else if ("resources".equals(name)) {
                    parseResources(child);
                } else if ("provides".equals(name)) {
                    parseProvides(child);
                } else if ("entrypoint".equals(name)) {
                    declaredEntrypoints.add(parseEntrypoint(child));
                } else if ("entrypoints".equals(name)) {
                    for (Element entry : childElements(child)) {
                        if ("entrypoint".equals(localName(entry))) {
                            declaredEntrypoints.add(parseEntrypoint(entry));
                        }
                    }
                }
            }

            if (mainClass != null && !mainClass.isEmpty()) {
                properties.put(FCModuleProperties.MAIN_CLASS, mainClass);
            }
            if ((version == null || version.isEmpty()) && properties.containsKey(FCModuleProperties.MODULE_VERSION)) {
                version = properties.get(FCModuleProperties.MODULE_VERSION);
            }
            if (version != null && !version.isEmpty()) {
                properties.put(FCModuleProperties.MODULE_VERSION, version);
            }
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Unable to parse FeatureCreep module.xml", e);
        }
    }

    private void parseProperties(Element propertiesElement) {
        for (Element property : childElements(propertiesElement)) {
            if (!"property".equals(localName(property))) {
                continue;
            }
            String name = attr(property, "name");
            if (name.isEmpty()) {
                continue;
            }
            String value = property.hasAttribute("value") ? property.getAttribute("value") : "true";
            properties.put(name, value);
        }
    }

    private void parseDependencies(Element dependenciesElement) throws IOException {
        for (Element dependency : childElements(dependenciesElement)) {
            if (!"module".equals(localName(dependency))) {
                continue;
            }
            String name = attr(dependency, "name");
            if (name.isEmpty()) {
                continue;
            }
            String services = attr(dependency, "services");
            boolean importServices = "true".equalsIgnoreCase(services)
                    || "import".equalsIgnoreCase(services)
                    || "export".equalsIgnoreCase(services);
            String versionRange = attr(dependency, "version-range");
            if (versionRange.isEmpty()) {
                versionRange = attr(dependency, "version");
            }
            String kindValue = attr(dependency, "kind");
            DependencyKind kind = kindValue.isEmpty()
                    ? (boolAttr(dependency, "optional", false) ? DependencyKind.OPTIONAL : DependencyKind.REQUIRED)
                    : DependencyKind.parse(kindValue);
            dependencies.add(new Dependency(name,
                    kind,
                    boolAttr(dependency, "export", false),
                    importServices,
                    versionRange.isEmpty() ? null : versionRange));
        }
    }

    private static Entrypoint parseEntrypoint(Element element) throws IOException {
        String type = attr(element, "type");
        String className = attr(element, "class");
        if (type.isEmpty() || className.isEmpty()) {
            throw new IOException("<entrypoint> requires type and class attributes");
        }
        return new Entrypoint(type, className, attr(element, "adapter"));
    }

    private void parseResources(Element resourcesElement) {
        for (Element resource : childElements(resourcesElement)) {
            if (!"resource-root".equals(localName(resource))) {
                continue;
            }
            String path = attr(resource, "path");
            if (!path.isEmpty() && !".".equals(path)) {
                resourceRoots.add(path);
            }
        }
    }

    private void parseProvides(Element providesElement) {
        for (Element service : childElements(providesElement)) {
            if (!"service".equals(localName(service))) {
                continue;
            }
            String serviceName = attr(service, "name");
            if (serviceName.isEmpty()) {
                continue;
            }
            List<String> implementations = providedServices.computeIfAbsent(serviceName, k -> new ArrayList<String>());
            for (Element withClass : childElements(service)) {
                if ("with-class".equals(localName(withClass))) {
                    String implementation = attr(withClass, "name");
                    if (!implementation.isEmpty()) {
                        implementations.add(implementation);
                    }
                }
            }
        }
    }

    private static List<Element> childElements(Element parent) {
        List<Element> children = new ArrayList<Element>();
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node instanceof Element) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private static String localName(Element element) {
        String local = element.getLocalName();
        return local == null ? element.getNodeName() : local;
    }

    private static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null ? "" : value.trim();
    }

    private static boolean boolAttr(Element element, String name, boolean defaultValue) {
        String value = attr(element, name);
        return value.isEmpty() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static void harden(DocumentBuilderFactory factory) {
        String[] features = {
                "http://apache.org/xml/features/disallow-doctype-decl",
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        };
        boolean[] values = { true, false, false, false };
        for (int i = 0; i < features.length; i++) {
            try {
                factory.setFeature(features[i], values[i]);
            } catch (ParserConfigurationException ignored) {
                // Parser implementation does not support this hardening flag.
            }
        }
        try { factory.setXIncludeAware(false); } catch (UnsupportedOperationException ignored) { }
        factory.setExpandEntityReferences(false);
    }
}
