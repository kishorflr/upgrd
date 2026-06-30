package com.upgrd.core.wildfly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WildFlyHttpProberTest {

    @TempDir
    Path tempDir;

    @Test
    void readsContextRootFromJbossWeb() throws Exception {
        Path migrated = tempDir.resolve("migrated/deploy/wildfly");
        Files.createDirectories(migrated);
        Files.writeString(migrated.resolve("jboss-web.xml"), """
                <jboss-web><context-root>my-app</context-root></jboss-web>
                """);
        assertEquals("my-app", new WildFlyHttpProber().readContextRoot(tempDir.resolve("migrated")));
    }

    @Test
    void probeOnceFailsWhenNothingListening() {
        var result = new WildFlyHttpProber().probeOnce("http://localhost:59999/no-app/");
        assertFalse(result.reachable());
    }
}
