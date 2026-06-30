package com.upgrd.core.war;

import com.upgrd.core.model.WarConflictPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class WarJavaStubGenerator {

    Path generateStub(Path stubRoot, String qualifiedName, String warClassEntry, WarConflictPolicy policy)
            throws IOException {
        int lastDot = qualifiedName.lastIndexOf('.');
        String packageName = lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
        String simpleName = lastDot > 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;

        Path packageDir = packageName.isBlank()
                ? stubRoot
                : stubRoot.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);
        Path stubFile = packageDir.resolve(simpleName + ".java");

        String content = """
                /*
                 * UPGRD WAR-AUTHORITY STUB — production class not in source tree
                 * WAR entry: %s
                 * Policy: %s
                 * Port implementation manually or decompile externally; bytecode copied to migrated WEB-INF/classes/
                 */
                %spublic class %s {
                    // WAR stub — replace with real implementation
                }
                """.formatted(
                warClassEntry,
                policy,
                packageName.isBlank() ? "" : "package " + packageName + ";\n\n",
                simpleName);

        Files.writeString(stubFile, content);
        return stubFile;
    }
}
