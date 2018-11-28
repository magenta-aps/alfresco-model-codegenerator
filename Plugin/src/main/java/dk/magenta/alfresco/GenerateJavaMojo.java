/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import static java.nio.file.StandardOpenOption.READ;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import jdk.nashorn.internal.ir.ContinueNode;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author martin
 */
@Mojo(name = "generate", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateJavaMojo extends AbstractMojo {

    public static final String DICTIONARY_URI = "http://www.alfresco.org/model/dictionary/1.0";
    public static final String MODEL_TAG = "model";
    public static final String ALFRESCO_GROUPID = "org.alfresco";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component(hint = "maven3")
    private DependencyGraphBuilder dependencyGraphBuilder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // If you want to filter out certain dependencies.
        //ArtifactFilter artifactFilter = new IncludesArtifactFilter(Arrays.asList(new String[]{"groupId:artifactId:version"}));
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        try {
            DependencyNode depenGraphRootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
            CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();
            depenGraphRootNode.accept(visitor);
            List<DependencyNode> children = visitor.getNodes();

            //getLog().info("CHILDREN ARE :");
            for (DependencyNode node : children) {
                Artifact atf = node.getArtifact();
                if (atf.getFile() == null) {
                    //getLog().info("Found artifact with null file: " + atf);
                    continue;
                }
//                if (!include(atf)) {
//                    continue;
//                }
                getLog().info("Artifact: " + atf.getFile());

                URI uri = URI.create("jar:file:" + atf.getFile().toString());
                Map<String, String> env = new HashMap<String, String>();
                env.put("create", "true");

                try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
                    for (Path path : zipfs.getRootDirectories()) {
                        //getLog().info("PATH: " + path);

                        Finder finder = new Finder(zipfs.getPathMatcher("glob:*.{xml}"));
                        Files.walkFileTree(path, finder);
                        finder.done();

                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    protected boolean include(Artifact artifact) {
        return artifact.getGroupId().equals(ALFRESCO_GROUPID) || artifact.getGroupId().equals(project.getGroupId());
    }

    protected class Finder
            extends SimpleFileVisitor<Path> {

        private final PathMatcher matcher;

        Finder(PathMatcher matcher) {
            this.matcher = matcher;//FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        }

        // Compares the glob pattern against
        // the file or directory name.
        void find(Path file) {
            Path name = file.getFileName();
            if (name != null && matcher.matches(name)) {
                try {
                    InputStream in = Files.newInputStream(file, READ);

                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(true);
                    factory.setValidating(false);
                    SAXParser saxParser = factory.newSAXParser();
                    saxParser.getXMLReader().setEntityResolver(new DummyEntityResolver());
                    SaxModuleParser moduleHandler = new SaxModuleParser(getLog(), Collections.EMPTY_LIST, project.getBuild().getSourceDirectory());

                    InputStream precheckStream = Files.newInputStream(file, READ);

                    //The SAX parser spends about 500ms pr xml file on downloading xsd's and other resources.
                    //The BufferedReader is a temporary measure to avoid starting the SAX parser the XML file is not a module file.
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(precheckStream, "UTF-8"))) {
                        while (reader.ready()) {
                            String line = reader.readLine();
                            if (line.contains("<?") || line.contains("<!")) {
                                continue;
                            }
                            if (line.contains("<")) {
                                if (line.contains("<model")) {
                                    long time = System.currentTimeMillis();
                                    try {
                                        saxParser.parse(in, moduleHandler);
                                    } catch (NotModuleException ex) {
                                        //It's fine. We found a false positive: An XML file which was not a module file. Keep going.
                                    } finally {
                                        getLog().info("Parsing took " + (System.currentTimeMillis() - time) + " for " + file);
                                        break;
                                    }
                                }else{
                                    break;
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Prints the total number of
        // matches to standard out.
        void done() {
            //getLog().info("Search done");
        }

        // Invoke the pattern matching
        // method on each file.
        @Override
        public FileVisitResult visitFile(Path file,
                BasicFileAttributes attrs) {
            find(file);
            return FileVisitResult.CONTINUE;
        }

        // Invoke the pattern matching
        // method on each directory.
        @Override
        public FileVisitResult preVisitDirectory(Path dir,
                BasicFileAttributes attrs) {
            find(dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file,
                IOException exc) {
            System.err.println(exc);
            return FileVisitResult.CONTINUE;
        }
    }

    public class DummyEntityResolver implements EntityResolver {

        public InputSource resolveEntity(String publicID, String systemID)
                throws SAXException {

            return new InputSource(new StringReader(""));
        }
    }

}
