package featurecreep.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
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

    public static final class Dependency {
        private final String name;
        private final boolean optional;
        private final boolean export;
        private final boolean services;

        Dependency(String name, boolean optional, boolean export, boolean services) {
            this.name = name;
            this.optional = optional;
            this.export = export;
            this.services = services;
        }

        public String getName() { return name; }
        public boolean isOptional() { return optional; }
        public boolean isExport() { return export; }
        public boolean isServices() { return services; }
    }

    private String declaredName;
    private String mainClass;
    private String version;
    private String xmlLocation;
    private final Map<String, String> properties = new LinkedHashMap<String, String>();
    private final List<Dependency> dependencies = new ArrayList<Dependency>();
    private final List<String> resourceRoots = new ArrayList<String>();
    private final Map<String, List<String>> providedServices = new LinkedHashMap<String, List<String>>();

    public String getDeclaredName() { return declaredName; }
    public String getMainClass() { return mainClass; }
    public String getVersion() { return version; }
    public String getXmlLocation() { return xmlLocation; }
    public Map<String, String> getProperties() { return Collections.unmodifiableMap(properties); }
    public List<Dependency> getDependencies() { return Collections.unmodifiableList(dependencies); }
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
        return result;
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
            if (entry.getKey() != null && entry.getValue() != null) {
                properties.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        mainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);
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
                }
            }

            if (mainClass != null && !mainClass.isEmpty()) {
                properties.put(FCModuleProperties.MAIN_CLASS, mainClass);
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

    private void parseDependencies(Element dependenciesElement) {
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
            dependencies.add(new Dependency(name,
                    boolAttr(dependency, "optional", false),
                    boolAttr(dependency, "export", false),
                    importServices));
        }
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
