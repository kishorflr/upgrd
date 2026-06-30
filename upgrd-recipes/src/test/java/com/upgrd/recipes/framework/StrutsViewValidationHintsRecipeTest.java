package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsViewValidationHintsRecipeTest {

    @Test
    void generatesJspHints() {
        String jsp = """
                <%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
                <html:form action="/user"><html:text property="username"/></html:form>
                """;
        var change = new StrutsViewValidationHintsRecipe().transform("pages/login.jsp", jsp);
        assertTrue(change.isPresent());
        assertTrue(change.get().after().contains("html:form"));
        assertTrue(change.get().relativePath().endsWith(".struts-view-hints.md"));
    }

    @Test
    void generatesValidationHints() {
        String xml = """
                <form-validation>
                  <formset>
                    <form name="userForm">
                      <field property="username" depends="required"/>
                    </form>
                  </formset>
                </form-validation>
                """;
        var change = new StrutsViewValidationHintsRecipe().transform("WEB-INF/validation.xml", xml);
        assertTrue(change.isPresent());
        assertTrue(change.get().after().contains("userForm"));
        assertTrue(change.get().after().contains("@NotBlank"));
    }
}
