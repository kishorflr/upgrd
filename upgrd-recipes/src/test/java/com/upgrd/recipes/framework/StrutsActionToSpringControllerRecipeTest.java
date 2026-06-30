package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsActionToSpringControllerRecipeTest {

    @Test
    void migratesStrutsAction() {
        String before = """
                package com.example;

                import org.apache.struts.action.Action;
                import org.apache.struts.action.ActionForm;
                import org.apache.struts.action.ActionForward;
                import org.apache.struts.action.ActionMapping;
                import javax.servlet.http.HttpServletRequest;
                import javax.servlet.http.HttpServletResponse;

                public class UserAction extends Action {
                    public ActionForward execute(ActionMapping mapping, ActionForm form,
                            HttpServletRequest request, HttpServletResponse response) throws Exception {
                        return mapping.findForward("success");
                    }
                }
                """;

        var change = new StrutsActionToSpringControllerRecipe().transform("UserAction.java", before);
        assertTrue(change.isPresent());
        String after = change.get().after();
        assertTrue(after.contains("@Controller"));
        assertTrue(after.contains("@GetMapping") || after.contains("@PostMapping"));
        assertTrue(after.contains("org.springframework"));
        assertTrue(!after.contains("org.apache.struts"));
        assertTrue(!after.contains("extends Action"));
        assertTrue(after.indexOf("package com.example;") < after.indexOf("import org.springframework"));
    }

    @Test
    void migratesExecuteWithBodyStatements() {
        String before = """
                package com.example;

                import org.apache.struts.action.Action;
                import org.apache.struts.action.ActionForm;
                import org.apache.struts.action.ActionForward;
                import org.apache.struts.action.ActionMapping;
                import javax.servlet.http.HttpServletRequest;
                import javax.servlet.http.HttpServletResponse;

                public class UserAction extends Action {
                    public ActionForward execute(ActionMapping mapping, ActionForm form,
                            HttpServletRequest request, HttpServletResponse response) throws Exception {
                        log.info("handling user request");
                        return mapping.findForward("success");
                    }
                }
                """;

        var change = new StrutsActionToSpringControllerRecipe().transform("UserAction.java", before);
        assertTrue(change.isPresent());
        String after = change.get().after();
        assertTrue(after.contains("@PostMapping") || after.contains("@GetMapping"));
        assertTrue(after.contains("log.info"));
        assertTrue(!after.contains("ActionMapping"));
    }

    @Test
    void usesStrutsConfigPathAndPostMappingWithModelAttribute() throws Exception {
        var recipe = new StrutsActionToSpringControllerRecipe();
        recipe.prepare(java.nio.file.Path.of(StrutsActionToSpringControllerRecipeTest.class.getClassLoader()
                .getResource("struts-config-fixture")
                .toURI()));

        String before = """
                package com.example;

                import org.apache.struts.action.Action;
                import org.apache.struts.action.ActionForm;
                import org.apache.struts.action.ActionForward;
                import org.apache.struts.action.ActionMapping;
                import javax.servlet.http.HttpServletRequest;
                import javax.servlet.http.HttpServletResponse;

                public class UserAction extends Action {
                    public ActionForward execute(ActionMapping mapping, ActionForm form,
                            HttpServletRequest request, HttpServletResponse response) throws Exception {
                        return mapping.findForward("success");
                    }
                }
                """;

        var change = recipe.transform("src/main/java/com/example/UserAction.java", before);
        assertTrue(change.isPresent());
        String after = change.get().after();
        assertTrue(after.contains("@PostMapping(\"/user\")"));
        assertTrue(after.contains("@ModelAttribute(\"userForm\")"));
        assertTrue(after.contains("@GetMapping(\"/user\")"));
        assertTrue(after.contains("showForm"));
        assertTrue(after.contains("submit"));
        assertTrue(after.contains("UserForm"));
        assertTrue(after.contains("return \"pages/login\""));
        assertTrue(after.contains("return \"pages/success\""));
    }
}
