/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import com.squareup.javapoet.ClassName;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
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
import java.util.regex.Pattern;
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

import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.FileUtils;

import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
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

    private List<TypePostfix> postfixes = new ArrayList<>();
    
    @Component(hint = "maven3")
    private DependencyGraphBuilder dependencyGraphBuilder;

    private ClassName baseNode;
    private ClassName baseAspect;
    private ClassName nameAnnotation;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        postfixes.add(new TypePostfix("http://www.alfresco.org/model/versionstore/2.0", null, "2"));
        baseNode = ClassName.get(packagePrefix, "NodeBase");
        baseAspect = ClassName.get(packagePrefix, "Aspect");
        nameAnnotation = ClassName.get(packagePrefix, "Name");

        List<String> packagePrefixes = Arrays.asList(packagePrefix.split("\\."));
        DOMModuleParser parser = new DOMModuleParser(getLog(), packagePrefixes, project.getBuild().getSourceDirectory(), baseNode, baseAspect, nameAnnotation, postfixes);
                                    
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

                        ArtifactParser finder = new ArtifactParser(zipfs.getPathMatcher("glob:*.{xml}"), parser);
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
        private final DOMModuleParser parser;

        ArtifactParser(PathMatcher matcher, DOMModuleParser parser) {
            this.matcher = matcher;//FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            this.parser = parser;
        }

        // Compares the glob pattern against
        // the file or directory name.
        void find(Path file) {
            Path name = file.getFileName();
            if (name != null && matcher.matches(name)) {
                try {
                    InputStream in = Files.newInputStream(file, READ);

                    
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
        File sourceDir = new File(project.getBuild().getSourceDirectory());
        File generationDir = new File(sourceDir, packagePrefix.replaceAll("\\.", "/"));
        FileUtils.forceMkdir(generationDir);
        generateClass("/dk/magenta/alfresco/anchor/NodeBase.java", generationDir, "NodeBase.java");
        generateClass("/dk/magenta/alfresco/anchor/Aspect.java", generationDir, "Aspect.java");
        generateClass("/dk/magenta/alfresco/anchor/Name.java", generationDir, "Name.java");
        
    }
    
    protected void generateClass(String resourceName, File generationDir, String targetName) throws FileNotFoundException, IOException{
        File targetFile = new File(generationDir, targetName);
        try(PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(targetFile), Charset.forName("UTF-8"))); BufferedReader in = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(resourceName), Charset.forName("UTF-8")))){
           while(in.ready()){
               String line = in.readLine();
               if(line.matches("^\\s*package (.+);")){
                   out.println("package "+packagePrefix+";");
               }else{
                   out.println(line);
               }
           }
        }
    }
//    protected CodeBlock getPackagesCodeBlock(){
//        TypeName ListOfStrings = ParameterizedTypeName.get(ClassName.get("java.util", "List"), ClassName.get(String.class));
//        ClassName linkedList = ClassName.get("java.util", "LinkedList");
//        ClassName arrayList = ClassName.get("java.util", "ArrayList");
//        return CodeBlock.builder()
//                .add("$[$T uri = qName.getNamespaceURI();$]", String.class)
//                .add("$[$T uriScanner = new $T(uri).useDelimiter(Pattern.compile(\"[\\\\/]\");$]", Scanner.class)
//                .add("$[$T uriPackages = new $T();$]", ListOfStrings, linkedList)
//                .add("$[while(uriScanner.hasnext(){uriPackages.add(uriScanner.next());})$]")
//                .add("$[while (uriPackages.get(0).startsWith(\"http\") || uriPackages.get(0).isEmpty()) {uriPackages.remove(0);}$]")
//                .add("$[if (uriPackages.get(0).contains(\".\")) {String addressPart = uriPackages.remove(0);Scanner addressScanner = new Scanner(addressPart).useDelimiter(\"[\\\\.]\");while (addressScanner.hasNext()) {String token = addressScanner.nextif (token.equals(\"www\")) {continue;}uriPackages.add(0, token);}}$]")
//                .add("$[$T packages = new $T();$]", ListOfStrings, arrayList)
//                .add("$[for (String uriPackage : uriPackages) {if (uriPackage.substring(0, 1).matches(\"^\\\\d\")) {uriPackage = \"_\" + uriPackage;}packages.add(uriPackage.replaceAll(\"\\\\.\", \"_\"));}$]")
//                .add("$[$T first = true;$]$[$T packageString = \"\";$]", Boolean.class, String.class)
//                .add("$[for (String pkg : packages) {if (!first) {packageString = packageString + \".\";}packageString = packageString + pkg;first = false;}$]$[return packageString;$]").build();
//    }
}
