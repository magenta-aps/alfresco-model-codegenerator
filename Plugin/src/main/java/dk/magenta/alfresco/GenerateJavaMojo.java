/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.io.BufferedReader;
import java.io.File;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
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
import javax.xml.parsers.ParserConfigurationException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.io.FileUtils;

import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.springframework.context.ApplicationContext;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author martin
 */
@Mojo(name = "generate", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateJavaMojo extends AbstractMojo {

    public static final String DICTIONARY_URI = "http://www.alfresco.org/model/dictionary/1.0";
    public static final String MODEL_TAG = "model";
    public static final String ALFRESCO_GROUPID = "org.alfresco";
    
    private static final String INCLUDE_EXCLUDE_DEFAULT = "||||";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project.groupId}.generated", readonly = true, required = true)
    private String packagePrefix;
    
    @Parameter(defaultValue = INCLUDE_EXCLUDE_DEFAULT, readonly = true, required = false)
    private String includeGroupID;
    
    @Parameter(defaultValue = INCLUDE_EXCLUDE_DEFAULT, readonly = true, required = false)
    private String includeArtifactID;
    
//    @Parameter(defaultValue = INCLUDE_EXCLUDE_DEFAULT, readonly = true, required = false)
//    private String includeFiles;
    
    @Parameter(defaultValue = INCLUDE_EXCLUDE_DEFAULT, readonly = true, required = false)
    private String excludeGroupID;
    
    @Parameter(defaultValue = INCLUDE_EXCLUDE_DEFAULT, readonly = true, required = false)
    private String excludeArtifactID;
    
    @Parameter(defaultValue = INCLUDE_EXCLUDE_DEFAULT, readonly = true, required = false)
    private String excludeFiles;

    @Component(hint = "maven3")
    private DependencyGraphBuilder dependencyGraphBuilder;

    private ClassName baseClass;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        baseClass = ClassName.get(packagePrefix, "Anchor");

        // If you want to filter out certain dependencies.
        //ArtifactFilter artifactFilter = new IncludesArtifactFilter(Arrays.asList(new String[]{"groupId:artifactId:version"}));
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        try {
            String codeGenDir = getGenerationDir();
            getLog().info("Generating classes in '" + codeGenDir + "'");
            File codeGenDirFile = new File(codeGenDir);
            if (codeGenDirFile.exists()) {
                FileUtils.forceDelete(codeGenDirFile);
            }

            generateBaseClass();

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
                if (!includeArtifact(atf)) {
                    continue;
                }
                //getLog().info("Artifact: " + atf.getFile());

                URI uri = URI.create("jar:file:" + atf.getFile().toString());
                Map<String, String> env = new HashMap<String, String>();
                env.put("create", "true");

                try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
                    for (Path path : zipfs.getRootDirectories()) {
                        //getLog().info("PATH: " + path);

                        ArtifactParser finder = new ArtifactParser(zipfs.getPathMatcher("glob:*.{xml}"));
                        Files.walkFileTree(path, finder);
                        List<Path> parsedFiles = finder.getParsedFiles();
                        if(parsedFiles != null && !parsedFiles.isEmpty()){
                            getLog().info("Parsed artifact: "+atf);
                            for(Path parsedFile : parsedFiles){
                                getLog().info("Parsed file "+parsedFile.toString());
                            }
                        }
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();

        }
    }
    
    protected boolean includeFile(Path path){
        String filename = path.getFileName().toString();
        return !filename.equalsIgnoreCase(excludeFiles);
    }

    protected boolean includeArtifact(Artifact artifact) {
        boolean include = true;
        
        if(!excludeGroupID.equals(INCLUDE_EXCLUDE_DEFAULT)){
            Pattern excludeGroupPattern = wildcardPattern(excludeGroupID);
            if(artifact.getGroupId().matches(excludeGroupPattern.pattern())){
                include = false;
            }
        }
        
        if(!excludeArtifactID.equals(INCLUDE_EXCLUDE_DEFAULT)){
            Pattern excludeArtifactPattern = wildcardPattern(includeArtifactID);
            if(artifact.getArtifactId().matches(excludeArtifactPattern.pattern())){
                include = false;
            }
        }
        
        boolean includeActive = includeGroupID.equals(INCLUDE_EXCLUDE_DEFAULT) && !includeArtifactID.equals(INCLUDE_EXCLUDE_DEFAULT);
        boolean includeG = true;
        boolean includeA = true;
        
        if(!includeGroupID.equals(INCLUDE_EXCLUDE_DEFAULT)){
            Pattern includeGroupPattern = wildcardPattern(includeGroupID);
            includeG = artifact.getGroupId().matches(includeGroupPattern.pattern());
        }
        
        if(!includeArtifactID.equals(INCLUDE_EXCLUDE_DEFAULT)){
            Pattern includeArtifactPattern = wildcardPattern(includeArtifactID);
            includeA = artifact.getArtifactId().matches(includeArtifactPattern.pattern());
        }
        
        if(includeActive){
            include = includeG && includeA;
        }
        
        return include;
    }
    
    protected Pattern wildcardPattern(String wildcardPattern){
        return Pattern.compile(wildcardPattern.replaceAll("//*", ".*"));
    }

    protected class ArtifactParser
            extends SimpleFileVisitor<Path> {

        private final PathMatcher matcher;
        private final List<Path> parsedFiles = new ArrayList<>();

        ArtifactParser(PathMatcher matcher) {
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
                    List<String> packagePrefixes = Arrays.asList(packagePrefix.split("\\."));

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
                                    DOMModuleParser parser = new DOMModuleParser(getLog(), packagePrefixes, project.getBuild().getSourceDirectory(), baseClass);
                                    try {
                                        parser.execute(in);
                                        parser.saveFiles();
                                        parsedFiles.add(file);
                                    } catch (IOException | ParserConfigurationException | SAXException ex) {
                                        getLog().warn("Could not parse file: ",ex);
                                    } finally {
                                        getLog().info("Parsing took " + (System.currentTimeMillis() - time) + " for " + file);
                                        
                                    }
//                                   try {
//                                        saxParser.parse(in, moduleHandler);
//                                    } catch (NotModuleException ex) {
//                                        //It's fine. We found a false positive: An XML file which was not a module file. Keep going.
//                                    } finally {
//                                        getLog().info("Parsing took " + (System.currentTimeMillis() - time) + " for " + file);
//                                        break;
//                                    }
                                } else {
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

        public List<Path> getParsedFiles(){
            return parsedFiles;
        }

        // Invoke the pattern matching
        // method on each file.
        @Override
        public FileVisitResult visitFile(Path file,
                BasicFileAttributes attrs
        ) {
            find(file);
            return FileVisitResult.CONTINUE;
        }

        // Invoke the pattern matching
        // method on each directory.
        @Override
        public FileVisitResult preVisitDirectory(Path dir,
                BasicFileAttributes attrs
        ) {
            find(dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file,
                IOException exc
        ) {
            System.err.println(exc);
            return FileVisitResult.CONTINUE;
        }
    }

    protected String getGenerationDir() {
        String sourceDir = project.getBuild().getSourceDirectory().endsWith("/") ? project.getBuild().getSourceDirectory() : project.getBuild().getSourceDirectory() + "/";
        return sourceDir + packagePrefix.replaceAll("\\.", "/");
    }

    protected void generateBaseClass() throws IOException {
        String getAppHelperMethodName = "getApplicationContext";
        ClassName apphelper = ClassName.get("org.alfresco.util", "ApplicationContextHelper");
        
        TypeSpec baseType = TypeSpec.classBuilder(baseClass).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addField(FieldSpec.builder(NodeRef.class, "nodeRef", Modifier.PRIVATE).build())
                .addField(FieldSpec.builder(QName.class, "qName", Modifier.PRIVATE).build())
                .addMethod(MethodSpec.methodBuilder("getNodeRef").returns(NodeRef.class).addModifiers(Modifier.PUBLIC).addCode("return this.nodeRef;$W").build())
                .addMethod(MethodSpec.methodBuilder("getQName").returns(QName.class).addModifiers(Modifier.PUBLIC).addCode("return this.qName;$W").build())
                .addMethod(MethodSpec.methodBuilder("setNodeRef")
                        .addParameter(NodeRef.class, "nodeRef").addCode("this.nodeRef = nodeRef;$W").build())
                .addMethod(MethodSpec.methodBuilder("setQName")
                        .addParameter(QName.class, "qName").addCode("this.qName = qName;$W").build())
                .addMethod(MethodSpec.methodBuilder(getAppHelperMethodName).addModifiers(Modifier.PROTECTED).returns(ApplicationContext.class)
                        .addCode("return $T.getApplicationContext();\n", apphelper).build())
                .addMethod(MethodSpec.methodBuilder("getNodeService").addModifiers(Modifier.PROTECTED).returns(NodeService.class)
                        .addCode("return $N().getBean($T.class);\n", getAppHelperMethodName, NodeService.class).build()).build();
                

        JavaFile.builder(baseClass.packageName(), baseType).build().writeTo(new File(project.getBuild().getSourceDirectory()));
    }

    public class DummyEntityResolver implements EntityResolver {

        public InputSource resolveEntity(String publicID, String systemID)
                throws SAXException {

            return new InputSource(new StringReader(""));
        }
    }

}
