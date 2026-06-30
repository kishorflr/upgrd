package com.upgrd.core.weblogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Edge-local WebLogic deploy scaffold validation (no container — production targets remote WLS).
 */
public final class WebLogicCliService {

    public static final String WAR_ARTIFACT = "app-web-1.0.0-SNAPSHOT.war";

    public WebLogicStatus status(Path outputDir) throws IOException {
        Path migrated = migratedRoot(outputDir);
        Path weblogicDir = migrated.resolve("deploy/weblogic");
        List<String> notes = new ArrayList<>();

        boolean scaffoldOk = Files.isRegularFile(weblogicDir.resolve("weblogic.xml"));
        notes.add("Scaffold: " + (scaffoldOk ? "present" : "missing — run upgrd apply first"));

        for (String file : List.of("weblogic.xml", "weblogic-application.xml", "deploy.sh", "README.md")) {
            Path path = weblogicDir.resolve(file);
            notes.add(file + ": " + (Files.isRegularFile(path) ? "present" : "missing"));
        }

        Path war = warPath(migrated);
        notes.add("WAR built: " + (Files.isRegularFile(war) ? war.getFileName() : "not yet — mvn -Pproduction-weblogic package"));

        return new WebLogicStatus(scaffoldOk, Files.isRegularFile(war), notes);
    }

    public WebLogicResult validate(Path outputDir) throws IOException {
        Path migrated = migratedRoot(outputDir);
        Path weblogicDir = migrated.resolve("deploy/weblogic");
        List<String> issues = new ArrayList<>();

        if (!Files.isRegularFile(weblogicDir.resolve("weblogic.xml"))) {
            issues.add("Missing deploy/weblogic/weblogic.xml");
        }
        if (!Files.isRegularFile(weblogicDir.resolve("deploy.sh"))) {
            issues.add("Missing deploy/weblogic/deploy.sh");
        } else {
            String script = Files.readString(weblogicDir.resolve("deploy.sh"));
            if (!script.contains("app-web") || !script.contains(".war")) {
                issues.add("deploy.sh does not reference app-web WAR path");
            }
            if (!script.contains("production-weblogic")) {
                issues.add("deploy.sh should reference mvn -Pproduction-weblogic package");
            }
        }

        Path war = warPath(migrated);
        if (!Files.isRegularFile(war)) {
            issues.add("WAR not built — run: mvn -f migrated/pom.xml -Pproduction-weblogic package");
        }

        Path weblogicXml = weblogicDir.resolve("weblogic.xml");
        if (!Files.isRegularFile(weblogicXml)) {
            issues.add("Missing deploy/weblogic/weblogic.xml");
        } else {
            String xml = Files.readString(weblogicXml);
            if (!xml.contains("context-root")) {
                issues.add("weblogic.xml missing context-root");
            }
        }

        if (issues.isEmpty()) {
            return new WebLogicResult(true, 0, "WebLogic deploy scaffold validated");
        }
        return new WebLogicResult(false, 1, String.join("; ", issues));
    }

    private Path migratedRoot(Path outputDir) {
        return outputDir.resolve("migrated").toAbsolutePath().normalize();
    }

    private Path warPath(Path migrated) {
        return migrated.resolve("app-web/target").resolve(WAR_ARTIFACT);
    }

    public record WebLogicStatus(boolean scaffoldPresent, boolean warBuilt, List<String> notes) {
    }

    public record WebLogicResult(boolean success, int exitCode, String message) {
    }
}
