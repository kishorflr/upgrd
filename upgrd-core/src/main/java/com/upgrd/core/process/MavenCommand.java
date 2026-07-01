package com.upgrd.core.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the Maven CLI for subprocess use (Windows {@code mvn.cmd}, {@code MAVEN_HOME}, PATH).
 */
public final class MavenCommand {

    private MavenCommand() {
    }

    public static String executable() {
        String fromHome = fromMavenHome();
        if (fromHome != null) {
            return fromHome;
        }
        return isWindows() ? "mvn.cmd" : "mvn";
    }

    public static boolean isAvailable() {
        try {
            Process process = new ProcessBuilder(executable(), "-version").start();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String fromMavenHome() {
        String home = System.getenv("MAVEN_HOME");
        if (home == null || home.isBlank()) {
            home = System.getenv("M2_HOME");
        }
        if (home == null || home.isBlank()) {
            return null;
        }
        Path bin = Path.of(home.trim(), "bin");
        Path candidate = bin.resolve(isWindows() ? "mvn.cmd" : "mvn");
        if (Files.isRegularFile(candidate)) {
            return candidate.toAbsolutePath().toString();
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
