package com.upgrd.core.discovery;

import com.upgrd.core.model.BuildSystem;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.TechnologyFingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ProjectDiscoveryService {

    private final TechnologyFingerprintScanner fingerprintScanner = new TechnologyFingerprintScanner();

    public ProjectDiscovery discover(Path sourceRoot) throws IOException {
        return discover(sourceRoot, null);
    }

    public ProjectDiscovery discover(Path sourceRoot, ProjectProfile profileOverride) throws IOException {
        BuildSystem buildSystem = detectBuildSystem(sourceRoot);
        String javaVersionHint = readJavaVersionHint(sourceRoot, buildSystem);
        List<String> sourceRoots = findSourceRoots(sourceRoot);
        List<String> descriptors = findWebDescriptors(sourceRoot);
        boolean weblogicApi = containsWeblogicReferences(sourceRoot, sourceRoots);

        TechnologyFingerprint fingerprint = fingerprintScanner.scan(sourceRoot, sourceRoots);
        ProjectProfile profile = profileOverride != null
                ? profileOverride
                : fingerprintScanner.inferProfile(fingerprint, descriptors);

        return new ProjectDiscovery(
                buildSystem,
                javaVersionHint,
                sourceRoots,
                descriptors,
                weblogicApi,
                fingerprint,
                profile);
    }

    private BuildSystem detectBuildSystem(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) {
            return BuildSystem.MAVEN;
        }
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            return BuildSystem.GRADLE;
        }
        if (Files.exists(root.resolve("build.xml"))) {
            return BuildSystem.ANT;
        }
        return BuildSystem.UNKNOWN;
    }

    private String readJavaVersionHint(Path root, BuildSystem buildSystem) throws IOException {
        if (buildSystem == BuildSystem.MAVEN && Files.exists(root.resolve("pom.xml"))) {
            String pom = Files.readString(root.resolve("pom.xml"));
            for (String token : List.of("<maven.compiler.release>", "<java.version>", "<maven.compiler.source>")) {
                int start = pom.indexOf(token);
                if (start >= 0) {
                    int valueStart = start + token.length();
                    int valueEnd = pom.indexOf('<', valueStart);
                    if (valueEnd > valueStart) {
                        return pom.substring(valueStart, valueEnd).trim();
                    }
                }
            }
        }
        if (Files.exists(root.resolve("build.xml"))) {
            String buildXml = Files.readString(root.resolve("build.xml"));
            if (buildXml.contains("source=\"1.7\"") || buildXml.contains("target=\"1.7\"")) {
                return "1.7";
            }
            if (buildXml.contains("source=\"1.8\"") || buildXml.contains("target=\"1.8\"")) {
                return "1.8";
            }
        }
        return "unknown";
    }

    private List<String> findSourceRoots(Path root) throws IOException {
        List<String> roots = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isDirectory)
                    .filter(path -> path.endsWith("src/main/java") || path.endsWith("src"))
                    .forEach(path -> roots.add(root.relativize(path).toString()));
        }
        if (roots.isEmpty() && Files.isDirectory(root)) {
            roots.add(".");
        }
        return roots.stream().distinct().sorted().toList();
    }

    private List<String> findWebDescriptors(Path root) throws IOException {
        List<String> descriptors = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .filter(name -> name.endsWith("web.xml")
                            || name.endsWith("weblogic.xml")
                            || name.endsWith("jboss-web.xml")
                            || name.endsWith("application.xml")
                            || name.endsWith("struts-config.xml"))
                    .forEach(descriptors::add);
        }
        return descriptors.stream().sorted().toList();
    }

    private boolean containsWeblogicReferences(Path root, List<String> sourceRoots) throws IOException {
        for (String sourceRoot : sourceRoots) {
            Path javaRoot = root.resolve(sourceRoot);
            if (!Files.isDirectory(javaRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(javaRoot)) {
                boolean found = files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .anyMatch(this::mentionsWeblogic);
                if (found) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean mentionsWeblogic(Path javaFile) {
        try {
            return Files.readString(javaFile).contains("weblogic.");
        } catch (IOException ex) {
            return false;
        }
    }
}
