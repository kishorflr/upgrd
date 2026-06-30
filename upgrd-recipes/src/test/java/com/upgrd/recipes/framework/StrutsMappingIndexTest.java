package com.upgrd.recipes.framework;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrutsMappingIndexTest {

    @Test
    void parsesActionMappings() {
        String config = """
                <?xml version="1.0" encoding="UTF-8"?>
                <struts-config>
                  <action-mappings>
                    <action path="/user" type="com.example.UserAction" name="userForm" scope="request">
                      <forward name="success" path="/pages/success.jsp"/>
                    </action>
                    <action path="/login" type="com.example.LoginAction"/>
                  </action-mappings>
                </struts-config>
                """;

        Map<String, StrutsMappingIndex.ActionMapping> mappings = StrutsMappingIndex.parse(config);
        assertEquals(2, mappings.size());
        assertEquals("/user", mappings.get("com.example.UserAction").path());
        assertEquals("userForm", mappings.get("com.example.UserAction").formName());
        assertEquals("/login", mappings.get("com.example.LoginAction").path());
        assertTrue(!mappings.get("com.example.LoginAction").hasForm());
    }

    @Test
    void findsMappingByClassName() {
        var index = new StrutsMappingIndex(StrutsMappingIndex.parse("""
                <action path="/admin" type="com.example.AdminAction" name="adminForm"/>
                """));

        var mapping = index.findForClass("AdminAction", "com.example");
        assertTrue(mapping.isPresent());
        assertEquals("/admin", mapping.get().path());
        assertEquals("adminForm", mapping.get().formName());
    }
}
