package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsJspToThymeleafRecipeTest {

    @Test
    void scaffoldsThymeleafFromStrutsJsp() {
        String jsp = """
                <%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
                <html:html>
                <html:form action="/user">
                  <html:text property="username"/>
                  <html:submit value="Login"/>
                </html:form>
                </html:html>
                """;
        var change = new StrutsJspToThymeleafRecipe().transform("pages/login.jsp", jsp);
        assertTrue(change.isPresent());
        String html = change.get().after();
        assertTrue(change.get().relativePath().contains("templates/pages/login.html"));
        assertTrue(html.contains("xmlns:th=\"http://www.thymeleaf.org\""));
        assertTrue(html.contains("th:action=\"@{/user}\""));
        assertTrue(html.contains("th:field=\"*{username}\""));
    }
}
