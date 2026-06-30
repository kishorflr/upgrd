<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<html:html>
<body>
  <html:form action="/user">
    <html:text property="username"/>
    <html:submit value="Login"/>
  </html:form>
  <bean:write name="userForm" property="username"/>
</body>
</html:html>
