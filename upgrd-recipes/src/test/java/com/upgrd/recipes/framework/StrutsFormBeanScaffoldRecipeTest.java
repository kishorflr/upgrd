package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsFormBeanScaffoldRecipeTest {

    @TempDir
    Path projectRoot;

    @Test
    void scaffoldsFormBeanFromStrutsConfigAndValidation() throws Exception {
        Path webInf = projectRoot.resolve("src/main/webapp/WEB-INF");
        Files.createDirectories(webInf);
        Files.writeString(webInf.resolve("struts-config.xml"), """
                <struts-config>
                  <form-beans>
                    <form-bean name="userForm" type="com.example.UserForm"/>
                  </form-beans>
                </struts-config>
                """);
        Files.writeString(webInf.resolve("validation.xml"), """
                <form-validation>
                  <formset>
                    <form name="userForm">
                      <field property="username" depends="required"/>
                      <field property="email" depends="required"/>
                    </form>
                  </formset>
                </form-validation>
                """);
        Files.createDirectories(projectRoot.resolve("src/main/java/com/example"));
        Files.writeString(projectRoot.resolve("src/main/java/com/example/UserAction.java"), """
                package com.example;
                public class UserAction {}
                """);

        var changes = new StrutsFormBeanScaffoldRecipe().generateChanges(projectRoot);
        assertTrue(changes.size() == 1);
        assertTrue(changes.get(0).relativePath().endsWith("com/example/UserForm.java"));
        String source = changes.get(0).after();
        assertTrue(source.contains("private String username"));
        assertTrue(source.contains("private String email"));
        assertTrue(source.contains("getUsername"));
    }
}
