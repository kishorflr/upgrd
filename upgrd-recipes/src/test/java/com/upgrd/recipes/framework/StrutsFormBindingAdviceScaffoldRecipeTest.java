package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsFormBindingAdviceScaffoldRecipeTest {

    @TempDir
    Path projectRoot;

    @Test
    void scaffoldsControllerAdviceForFormBeans() throws Exception {
        Path webInf = projectRoot.resolve("src/main/webapp/WEB-INF");
        Files.createDirectories(webInf);
        Files.writeString(webInf.resolve("struts-config.xml"), """
                <struts-config>
                  <form-beans>
                    <form-bean name="userForm" type="com.example.UserForm"/>
                  </form-beans>
                </struts-config>
                """);
        Files.createDirectories(projectRoot.resolve("src/main/java/com/example"));
        Files.writeString(projectRoot.resolve("src/main/java/com/example/UserAction.java"), """
                package com.example;
                public class UserAction {}
                """);

        var changes = new StrutsFormBindingAdviceScaffoldRecipe().generateChanges(projectRoot);
        assertTrue(changes.size() == 1);
        String source = changes.get(0).after();
        assertTrue(source.contains("@ControllerAdvice"));
        assertTrue(source.contains("@ModelAttribute(\"userForm\")"));
        assertTrue(source.contains("@InitBinder(\"userForm\")"));
        assertTrue(source.contains("new UserForm()"));
        assertTrue(source.contains("setDisallowedFields"));
    }
}
