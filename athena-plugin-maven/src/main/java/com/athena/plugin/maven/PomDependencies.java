package com.athena.plugin.maven;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * One pom.xml's own artifactId and its declared dependencies (ticket #340), keyed
 * {@code groupId:artifactId} — with {@code " [managed]"} appended for {@code <dependencyManagement>}
 * entries, so a managed and a direct dependency on the same artifact are distinct. Values are as
 * written: properties aren't resolved and no effective POM is computed.
 */
final class PomDependencies {

    private final String artifactId;
    private final Map<String, Dependency> dependencies;

    private PomDependencies(String artifactId, Map<String, Dependency> dependencies) {
        this.artifactId = artifactId;
        this.dependencies = Collections.unmodifiableMap(new LinkedHashMap<>(dependencies));
    }

    /**
     * A declared dependency's scope (as written; direct dependencies default to compile), version,
     * optionality and excluded transitive dependencies ({@code groupId:artifactId}, sorted).
     */
    record Dependency(String scope, String version, boolean optional, SortedSet<String> exclusions) {

        Dependency {
            exclusions = Collections.unmodifiableSortedSet(new TreeSet<>(exclusions));
        }
    }

    /** The parsed pom, or empty when {@code xml} isn't a well-formed Maven project. */
    static Optional<PomDependencies> parse(String xml) {
        return parse(new InputSource(new StringReader(xml)));
    }

    /**
     * The parsed pom, or empty when {@code bytes} aren't a well-formed Maven project. Parsing the raw
     * bytes lets the XML declaration's encoding (e.g. ISO-8859-1) apply instead of assuming UTF-8.
     */
    static Optional<PomDependencies> parse(byte[] bytes) {
        return parse(new InputSource(new ByteArrayInputStream(bytes)));
    }

    private static Optional<PomDependencies> parse(InputSource source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Document document = builder.parse(source);
            Element project = document.getDocumentElement();
            if (!"project".equals(project.getLocalName() == null ? project.getTagName() : project.getLocalName())) {
                return Optional.empty();
            }
            Map<String, Dependency> dependencies = new LinkedHashMap<>();
            child(project, "dependencies").ifPresent(list -> collect(list, false, dependencies));
            child(project, "dependencyManagement").flatMap(management -> child(management, "dependencies"))
                    .ifPresent(list -> collect(list, true, dependencies));
            String artifactId = child(project, "artifactId").map(Node::getTextContent).map(String::trim).orElse("");
            return Optional.of(new PomDependencies(artifactId, dependencies));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return Optional.empty();
        }
    }

    String artifactId() {
        return artifactId;
    }

    Map<String, Dependency> dependencies() {
        return dependencies;
    }

    private static void collect(Element list, boolean managed, Map<String, Dependency> into) {
        NodeList children = list.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element dependency && "dependency".equals(dependency.getTagName())) {
                String groupId = text(dependency, "groupId");
                String artifactId = text(dependency, "artifactId");
                if (artifactId.isEmpty()) continue;
                String scope = text(dependency, "scope");
                into.put(groupId + ":" + artifactId + (managed ? " [managed]" : ""), new Dependency(
                        scope.isEmpty() && !managed ? "compile" : scope, text(dependency, "version"),
                        "true".equals(text(dependency, "optional")), exclusions(dependency)));
            }
        }
    }

    private static SortedSet<String> exclusions(Element dependency) {
        SortedSet<String> exclusions = new TreeSet<>();
        child(dependency, "exclusions").ifPresent(list -> {
            NodeList children = list.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element exclusion && "exclusion".equals(exclusion.getTagName())) {
                    exclusions.add(text(exclusion, "groupId") + ":" + text(exclusion, "artifactId"));
                }
            }
        });
        return exclusions;
    }

    private static String text(Element parent, String name) {
        return child(parent, name).map(Node::getTextContent).map(String::trim).orElse("");
    }

    private static Optional<Element> child(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && name.equals(element.getTagName())) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }
}
