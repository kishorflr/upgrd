package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(source.contains("@NotNull"));
    }

    @Test
    void convertsExistingActionFormSubclassInPlace() throws Exception {
        Path webInf = projectRoot.resolve("src/main/webapp/WEB-INF");
        Files.createDirectories(webInf);
        Files.writeString(webInf.resolve("struts-config.xml"), """
                <struts-config>
                  <form-beans>
                    <form-bean name="loginForm" type="com.demo.verify.LoginForm"/>
                  </form-beans>
                </struts-config>
                """);
        Files.writeString(webInf.resolve("validation.xml"), """
                <form-validation>
                  <formset>
                    <form name="loginForm">
                      <field property="username" depends="required"/>
                    </form>
                  </formset>
                </form-validation>
                """);
        Path formPath = projectRoot.resolve("src/main/java/com/demo/verify/LoginForm.java");
        Files.createDirectories(formPath.getParent());
        Files.writeString(formPath, """
                package com.demo.verify;

                import org.apache.struts.action.ActionForm;

                public class LoginForm extends ActionForm {
                    private String username;
                    private String password;

                    public String getUsername() { return username; }
                    public void setUsername(String username) { this.username = username; }
                    public String getPassword() { return password; }
                    public void setPassword(String password) { this.password = password; }
                }
                """);
        Files.writeString(projectRoot.resolve("src/main/java/com/demo/verify/LoginAction.java"), """
                package com.demo.verify;
                public class LoginAction {}
                """);

        var changes = new StrutsFormBeanScaffoldRecipe().generateChanges(projectRoot);
        assertTrue(changes.size() == 1);
        String source = changes.get(0).after();
        assertFalse(source.contains("ActionForm"));
        assertFalse(source.contains("org.apache.struts"));
        assertTrue(source.contains("private String username"));
        assertTrue(source.contains("private String password"));
        assertTrue(source.contains("@NotNull"));
    }

    @Test
    void scaffoldsSizeFromMinlengthAndMaxlength() throws Exception {
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
                      <field property="username" depends="required,minlength">
                        <var><var-name>minlength</var-name><var-value>3</var-value></var>
                      </field>
                      <field property="password" depends="maxlength">
                        <var><var-name>maxlength</var-name><var-value>32</var-value></var>
                      </field>
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
        String source = changes.get(0).after();
        assertTrue(source.contains("@Size(min = 3)"));
        assertTrue(source.contains("@Size(max = 32)"));
    }

    @Test
    void scaffoldsEmailValidation() throws Exception {
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
                      <field property="email" depends="required,email"/>
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
        String source = changes.get(0).after();
        assertTrue(source.contains("@NotNull"));
        assertTrue(source.contains("@Email"));
    }
}
