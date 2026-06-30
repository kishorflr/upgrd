package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsMappingIndexTest {

    @Test
    void parsesActionMappingsWithForwards() {
        String config = """
                <?xml version="1.0" encoding="UTF-8"?>
                <struts-config>
                  <form-beans>
                    <form-bean name="userForm" type="com.example.UserForm"/>
                  </form-beans>
                  <action-mappings>
                    <action path="/user" type="com.example.UserAction" name="userForm" scope="request">
                      <forward name="input" path="/pages/login.jsp"/>
                      <forward name="success" path="/pages/success.jsp"/>
                    </action>
                    <action path="/login" type="com.example.LoginAction"/>
                  </action-mappings>
                </struts-config>
                """;

        Map<String, StrutsMappingIndex.FormBean> formBeans = StrutsMappingIndex.parseFormBeans(config);
        assertEquals("com.example.UserForm", formBeans.get("userForm").type());

        Map<String, StrutsMappingIndex.ActionMapping> mappings = StrutsMappingIndex.parse(config);
        assertEquals(2, mappings.size());
        var user = mappings.get("com.example.UserAction");
        assertEquals("/user", user.path());
        assertEquals("userForm", user.formName());
        assertEquals("com.example.UserForm", user.formBeanType());
        assertEquals("/pages/success.jsp", user.forwards().get("success"));
        assertEquals("pages/success", user.resolveView("success"));
        assertEquals("pages/login", user.resolveInputView("success"));
        assertEquals("/login", mappings.get("com.example.LoginAction").path());
        assertTrue(!mappings.get("com.example.LoginAction").hasForm());
    }

    @Test
    void findsMappingByClassName() {
        var index = new StrutsMappingIndex(
                StrutsMappingIndex.parse("""
                        <form-beans>
                          <form-bean name="adminForm" type="com.example.AdminForm"/>
                        </form-beans>
                        <action path="/admin" type="com.example.AdminAction" name="adminForm">
                          <forward name="ok" path="/admin/home.jsp"/>
                        </action>
                        """),
                StrutsMappingIndex.parseFormBeans("""
                        <form-bean name="adminForm" type="com.example.AdminForm"/>
                        """));

        var mapping = index.findForClass("AdminAction", "com.example");
        assertTrue(mapping.isPresent());
        assertEquals("/admin", mapping.get().path());
        assertEquals("adminForm", mapping.get().formName());
        assertEquals("admin/home", mapping.get().resolveView("ok"));
    }

    @Test
    void jspPathToViewNameStripsPrefixAndExtension() {
        assertEquals("pages/success", StrutsMappingIndex.jspPathToViewName("/pages/success.jsp"));
    }
}
