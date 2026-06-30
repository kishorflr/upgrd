package com.upgrd.core.apply;

import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployProfileScaffolderTest {

    @TempDir
    Path tempDir;

    @Test
    void scaffoldsWildFlyAndWebLogicProfiles() throws Exception {
        Path migrated = tempDir.resolve("migrated");
        Files.createDirectories(migrated);

        var scaffolder = new DeployProfileScaffolder();
        List<String> wildfly = scaffolder.scaffoldWildFly(migrated, "app-web");
        List<String> weblogic = scaffolder.scaffoldWebLogic(migrated, "weblogic-14c");

        assertTrue(wildfly.size() >= 4);
        assertTrue(weblogic.size() >= 6);
        assertTrue(Files.isRegularFile(migrated.resolve("deploy/weblogic/wldeploy.sh")));
        assertTrue(Files.isRegularFile(migrated.resolve("deploy/weblogic/wldeploy.properties")));
        assertTrue(Files.isRegularFile(migrated.resolve("deploy/wildfly/jboss-web.xml")));
        assertTrue(Files.isRegularFile(migrated.resolve("deploy/weblogic/weblogic.xml")));
        assertTrue(Files.isRegularFile(migrated.resolve("deploy/wildfly/docker-compose.yml")));
    }
}
