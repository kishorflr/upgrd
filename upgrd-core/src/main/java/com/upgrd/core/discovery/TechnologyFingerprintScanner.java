package com.upgrd.core.discovery;

import com.upgrd.core.model.LoggingFramework;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.ServletApi;
import com.upgrd.core.model.TechnologyFingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scans source trees and lib folders for framework, logging, and API signals.
 */
public final class TechnologyFingerprintScanner {

    public TechnologyFingerprint scan(Path sourceRoot, List<String> sourceRoots) throws IOException {
        ScanState state = new ScanState();

        scanLibFolders(sourceRoot, state);

        for (String sourceRootRel : sourceRoots) {
            Path javaRoot = sourceRoot.resolve(sourceRootRel);
            if (!Files.isDirectory(javaRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(javaRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(this::isScannable)
                        .forEach(path -> scanFile(path, sourceRoot, state));
            }
        }

        return new TechnologyFingerprint(
                List.copyOf(state.frameworks),
                resolveLogging(state),
                resolveServletApi(state),
                resolvePersistence(state),
                List.copyOf(state.riskSignals),
                List.copyOf(state.evidence));
    }

    public ProjectProfile inferProfile(TechnologyFingerprint fingerprint, List<String> webDescriptors) {
        boolean hasWeb = !webDescriptors.isEmpty()
                || fingerprint.frameworks().stream().anyMatch(this::isWebFramework);

        if (hasWeb) {
            return ProjectProfile.LEGACY_WEB;
        }
        if (fingerprint.frameworks().isEmpty()
                && fingerprint.servletApi() == ServletApi.NONE) {
            return ProjectProfile.LEGACY_BACKEND;
        }
        if (fingerprint.riskSignals().contains("god-class")
                || fingerprint.riskSignals().contains("raw-collections")
                || fingerprint.riskSignals().contains("legacy-collections")) {
            return ProjectProfile.LEGACY_BACKEND;
        }
        return ProjectProfile.UNKNOWN;
    }

    private boolean isWebFramework(String framework) {
        return framework.startsWith("SPRING_MVC")
                || framework.startsWith("STRUTS")
                || framework.equals("JSP");
    }

    private boolean isScannable(Path path) {
        String name = path.toString();
        return name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".properties");
    }

    private void scanLibFolders(Path sourceRoot, ScanState state) throws IOException {
        for (Path libDir : List.of(
                sourceRoot.resolve("lib"),
                sourceRoot.resolve("WebContent/WEB-INF/lib"),
                sourceRoot.resolve("WEB-INF/lib"))) {
            if (!Files.isDirectory(libDir)) {
                continue;
            }
            try (Stream<Path> jars = Files.list(libDir)) {
                jars.filter(path -> path.toString().endsWith(".jar"))
                        .map(path -> path.getFileName().toString().toLowerCase())
                        .forEach(jar -> inspectJar(jar, state));
            }
        }
    }

    private void inspectJar(String jar, ScanState state) {
        state.evidence.add("classpath:" + jar);
        if (jar.contains("spring-webmvc") || jar.contains("spring-web")) {
            state.frameworks.add(jar.contains("4.") || jar.contains("4-") ? "SPRING_MVC_4" : "SPRING_MVC");
        }
        if (jar.contains("struts")) {
            state.frameworks.add(jar.contains("2.") ? "STRUTS_2" : "STRUTS_1");
        }
        if (jar.contains("log4j-1") || jar.equals("log4j.jar")) {
            state.log4j1 = true;
        }
        if (jar.contains("log4j-core") || jar.contains("log4j-api")) {
            state.log4j2 = true;
        }
        if (jar.contains("slf4j")) {
            state.slf4j = true;
        }
        if (jar.contains("hibernate")) {
            state.frameworks.add("HIBERNATE");
        }
    }

    private void scanFile(Path file, Path sourceRoot, ScanState state) {
        try {
            String content = Files.readString(file);
            String rel = sourceRoot.relativize(file).toString();

            if (content.contains("org.springframework.web")) {
                boolean springMvc4 = content.contains("springframework.web.servlet")
                        || content.contains("springframework.web.bind");
                state.frameworks.add(springMvc4 ? "SPRING_MVC_4" : "SPRING_MVC");
                state.evidence.add(rel + ": import org.springframework.web");
            }
            if (content.contains("org.apache.struts") || content.contains("struts-config")) {
                state.frameworks.add(content.contains("struts2") || content.contains("StrutsPrepareAndExecuteFilter")
                        ? "STRUTS_2" : "STRUTS_1");
                state.evidence.add(rel + ": Struts usage");
            }
            if (content.contains("org.apache.log4j.")) {
                state.log4j1 = true;
                state.evidence.add(rel + ": import org.apache.log4j");
            }
            if (content.contains("org.apache.logging.log4j")) {
                state.log4j2 = true;
            }
            if (content.contains("org.slf4j")) {
                state.slf4j = true;
            }
            if (content.contains("java.util.logging")) {
                state.jul = true;
            }
            if (content.contains("javax.servlet")) {
                state.javaxServlet = true;
                state.evidence.add(rel + ": javax.servlet");
            }
            if (content.contains("jakarta.servlet")) {
                state.jakartaServlet = true;
            }
            if (content.contains("<%") || content.contains("jsp:")) {
                state.frameworks.add("JSP");
            }
            if (content.contains("java.sql.")) {
                state.evidence.add(rel + ": java.sql");
            }

            if (content.contains("Vector") || content.contains("Hashtable")) {
                state.riskSignals.add("legacy-collections");
                state.evidence.add(rel + ": Vector/Hashtable usage");
            }
            if (content.contains("@SuppressWarnings(\"rawtypes\")")) {
                state.riskSignals.add("raw-collections");
                state.evidence.add(rel + ": @SuppressWarnings(\"rawtypes\")");
            }
            long lineCount = content.lines().count();
            if (lineCount > 500) {
                state.riskSignals.add("god-class");
                state.evidence.add(rel + ": " + lineCount + " lines");
            }
            if (content.contains("static ") && content.contains(" = new ") && !content.contains("final")) {
                state.riskSignals.add("mutable-static-state");
            }
        } catch (IOException ignored) {
            // skip unreadable files
        }
    }

    private LoggingFramework resolveLogging(ScanState state) {
        int count = (state.log4j1 ? 1 : 0) + (state.log4j2 ? 1 : 0)
                + (state.slf4j ? 1 : 0) + (state.jul ? 1 : 0);
        if (count > 1) {
            return LoggingFramework.MIXED;
        }
        if (state.log4j1) return LoggingFramework.LOG4J_1;
        if (state.log4j2) return LoggingFramework.LOG4J_2;
        if (state.slf4j) return LoggingFramework.SLF4J;
        if (state.jul) return LoggingFramework.JUL;
        return LoggingFramework.UNKNOWN;
    }

    private ServletApi resolveServletApi(ScanState state) {
        if (state.javaxServlet && state.jakartaServlet) return ServletApi.MIXED;
        if (state.javaxServlet) return ServletApi.JAVAX;
        if (state.jakartaServlet) return ServletApi.JAKARTA;
        return ServletApi.NONE;
    }

    private String resolvePersistence(ScanState state) {
        if (state.frameworks.contains("HIBERNATE")) {
            return "HIBERNATE";
        }
        if (state.evidence.stream().anyMatch(e -> e.contains("java.sql"))) {
            return "JDBC";
        }
        return "UNKNOWN";
    }

    private static final class ScanState {
        private final Set<String> frameworks = new LinkedHashSet<>();
        private final Set<String> evidence = new LinkedHashSet<>();
        private final Set<String> riskSignals = new LinkedHashSet<>();
        private boolean log4j1;
        private boolean log4j2;
        private boolean slf4j;
        private boolean jul;
        private boolean javaxServlet;
        private boolean jakartaServlet;
    }
}
