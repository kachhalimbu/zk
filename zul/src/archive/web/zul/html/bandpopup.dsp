<%--
bandpopup.dsp

{{IS_NOTE
	Purpose:

	Description:

	History:
		Mon Mar 20 12:43:49     2006, Created by tomyeh
}}IS_NOTE

Copyright (C) 2006 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
}}IS_RIGHT
--%><%@ taglib uri="http://www.zkoss.org/dsp/web/core" prefix="c" %>
<%@ taglib uri="http://www.zkoss.org/dsp/zk/core" prefix="z" %>
<c:set var="self" value="${requestScope.arg.self}"/>
<div id="${self.uuid}"${self.outerAttrs}${self.innerAttrs}><c:forEach var="child" items="${self.children}">${z:redraw(child, null)}</c:forEach></div>