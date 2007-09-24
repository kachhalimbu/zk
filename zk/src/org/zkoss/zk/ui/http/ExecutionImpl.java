/* ExecutionImpl.java

{{IS_NOTE
	Purpose:
		
	Description:
		
	History:
		Mon Jun  6 14:14:02     2005, Created by tomyeh
}}IS_NOTE

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.ui.http;

import java.util.Collection;
import java.util.Map;
import java.util.Enumeration;
import java.io.Writer;
import java.io.Reader;
import java.io.IOException;
import java.security.Principal;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;

import org.zkoss.lang.Classes;
import org.zkoss.xel.Expressions;
import org.zkoss.xel.Expression;
import org.zkoss.xel.ExpressionFactory;
import org.zkoss.xel.VariableResolver;
import org.zkoss.xel.FunctionMapper;
import org.zkoss.idom.Document;

import org.zkoss.web.Attributes;
import org.zkoss.web.servlet.Servlets;
import org.zkoss.web.servlet.http.HttpBufferedResponse;
import org.zkoss.web.servlet.http.Encodes;
import org.zkoss.web.servlet.xel.RequestContexts;
import org.zkoss.web.servlet.xel.RequestContext;
import org.zkoss.web.servlet.xel.RequestXelResolver;
import org.zkoss.web.servlet.xel.AttributesMap;
import org.zkoss.web.util.resource.ClassWebResource;

import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.impl.AbstractExecution;
import org.zkoss.zk.ui.metainfo.PageDefinition;
import org.zkoss.zk.ui.metainfo.PageDefinitions;
import org.zkoss.zk.xel.Evaluator;
import org.zkoss.zk.xel.impl.SimpleEvaluator;
import org.zkoss.zk.xel.impl.ExecutionResolver;
import org.zkoss.zk.ui.sys.WebAppCtrl;
import org.zkoss.zk.ui.sys.RequestInfo;
import org.zkoss.zk.ui.impl.RequestInfoImpl;

/**
 * An {@link org.zkoss.zk.ui.Execution} implementation for HTTP request
 * and response.
 *
 * @author tomyeh
 */
public class ExecutionImpl extends AbstractExecution {
	private final ServletContext _ctx;
	private final HttpServletRequest _request;
	private final HttpServletResponse _response;
	private final RequestContext _xelctx;
	private final Map _attrs;
	private MyEval _eval;
	private ExecutionResolver _resolver;
	private boolean _voided;

	/** Constructs an execution for HTTP request.
	 * @param creating which page is being creating for this execution, or
	 * null if none is being created.
	 * {@link #isAsyncUpdate} returns based on this.
	 */
	public ExecutionImpl(ServletContext ctx, HttpServletRequest request,
	HttpServletResponse response, Desktop desktop, Page creating) {
		super(desktop, creating);
		_ctx = ctx;
		_request = request;
		_response = response;
		_xelctx = new ReqContext();

		_attrs = new AttributesMap() {
			protected Enumeration getKeys() {
				return _request.getAttributeNames();
			}
			protected Object getValue(String key) {
				return _request.getAttribute(key);
			}
			protected void setValue(String key, Object val) {
				_request.setAttribute(key, val);
			}
			protected void removeValue(String key) {
				_request.removeAttribute(key);
			}
		};
	}

	public void onActivate() {
		super.onActivate();
		RequestContexts.push(_xelctx);
	}
	public void onDeactivate() {
		RequestContexts.pop();
		super.onDeactivate();
	}

	//-- Execution --//
	public String[] getParameterValues(String name) {
		return _request.getParameterValues(name);
	}
	public String getParameter(String name) {
		return _request.getParameter(name);
	}
	public Map getParameterMap() {
		return _request.getParameterMap();
	}

	public VariableResolver getVariableResolver() {
		if (_resolver == null)
			_resolver =
				new ExecutionResolver(this,
					new RequestXelResolver(_ctx, _request, _response) {
						public ExpressionFactory getExpressionFactory() {
							return ExecutionImpl.this.getExpressionFactory();
						}
					});
		return _resolver;
	}
	private ExpressionFactory getExpressionFactory() {
		//TODO: how to make it depends on page's expf
		return Expressions.newExpressionFactory();
	}

	public Evaluator getEvaluator(Page page, Class expfcls) {
		if (page == null) {
			page = getCurrentPage();
			if (page == null) {
				final Collection c = getDesktop().getPages();
				if (!c.isEmpty()) page = (Page)c.iterator().next();
			}
		}

		if (_eval == null || _eval.page != page
		|| _eval.getExpressionFactoryClass() != expfcls)
			_eval = new MyEval(page, expfcls);
		return _eval;
	}
	public Evaluator getEvaluator(Component comp, Class expfcls) {
		return getEvaluator(comp != null ? comp.getPage(): null, expfcls);
	}

	public Object evaluate(Component comp, String expr, Class expectedType) {
		return evaluate0(comp, expr, expectedType,
			comp != null ? comp.getPage(): null);
	}
	public Object evaluate(Page page, String expr, Class expectedType) {
		return evaluate0(page, expr, expectedType, page);
	}
	private Object evaluate0(Object self, String expr,
	Class expectedType, Page page) {
		if (expr == null || expr.length() == 0 || expr.indexOf("${") < 0) {
			if (expectedType == Object.class || expectedType == String.class)
				return expr;
			return Classes.coerce(expectedType, expr);
		}

		final Evaluator eval = getEvaluator(page, null);
		final Expression expression = eval.parseExpression(expr, expectedType);
		return self instanceof Page ?
			eval.evaluate((Page)self, expression):
			eval.evaluate((Component)self, expression);
	}

	public void include(Writer out, String page, Map params, int mode)
	throws IOException {
		final HttpServletResponse bufresp =
			HttpBufferedResponse.getInstance(_response, out);
		try {
			if ((mode == PASS_THRU_ATTR || params == null)
			&& page.startsWith("~./")) {
				//Bug 1801028: We cannot invoke ZumlExtendlet directly
				//The real reason is unknown yet -- it could be due to
				//the re-creation of ExecutionImpl
				//However, the performance is not a major issue, so just skip
				final ClassWebResource cwr =
					WebManager.getWebManager(_ctx).getClassWebResource();
				if (!isZumlExtendlet(cwr, page)) {
					Object old = null;
					if (mode == PASS_THRU_ATTR) {
						old = _request.getAttribute(Attributes.ARG);
						_request.setAttribute(Attributes.ARG, params);
					}
					try {
						cwr.service(_request, bufresp, page.substring(2));
						return; //done
					} finally {
						if (mode == PASS_THRU_ATTR)
							_request.setAttribute(Attributes.ARG, old);
					}
				}
			}

			Servlets.include(_ctx, _request, bufresp, page, params, mode);
				//we don't use PageContext.include because Servlets.include
				//support ~xxx/ and other features.
		} catch (ServletException ex) {
			throw new UiException(ex);
		}
	}
	/** Returns whether the specified extension is served by
	 * {@link ZumlExtendlet}.
	 */
	private static boolean isZumlExtendlet(ClassWebResource cwr, String path) {
		final String ext = Servlets.getExtension(path);
		return ext != null && cwr.getExtendlet(ext) instanceof ZumlExtendlet;
	}
	public void include(String page)
	throws IOException {
		include(null, page, null, 0);
	}
	public void forward(Writer out, String page, Map params, int mode)
	throws IOException {
		if (getVisualizer().isEverAsyncUpdate())
			throw new IllegalStateException("Use sendRedirect instead when processing user's request");

		setVoided(true);

		try {
			Servlets.forward(_ctx, _request,
				HttpBufferedResponse.getInstance(_response, out),
				page, params, mode);
				//we don't use PageContext.forward because Servlets.forward
				//support ~xxx/ and other features.
		} catch (ServletException ex) {
			throw new UiException(ex);
		}
	}
	public void forward(String page)
	throws IOException {
		forward(null, page, null, 0);
	}
	public boolean isIncluded() {
		return Servlets.isIncluded(_request);
	}
	public boolean isForwarded() {
		return Servlets.isForwarded(_request);
	}

	public boolean isVoided() {
		return _voided;
	}
	public void setVoided(boolean voided) {
		_voided = voided;
	}

	public String encodeURL(String uri) {
		try {
			return Encodes.encodeURL(_ctx, _request, _response, uri);
		} catch (ServletException ex) {
			throw new UiException(ex);
		}
	}

	public Principal getUserPrincipal() {
		return _request.getUserPrincipal();
	}
	public boolean isUserInRole(String role) {
		return _request.isUserInRole(role);
	}
	public String getRemoteUser() {
		return _request.getRemoteUser();
	}
	public String getRemoteName() {
		return _request.getRemoteHost();
	}
	public String getRemoteAddr() {
		return _request.getRemoteAddr();
	}
	public String getServerName() {
		return _request.getServerName();
	}
	public int getServerPort() {
		return _request.getServerPort();
	}
	public String getLocalName() {
		return _request.getLocalName();
	}
	public String getLocalAddr() {
		return _request.getLocalAddr();
	}
	public int getLocalPort() {
		return _request.getLocalPort();
	}
	public String getContextPath() {
		return _request.getContextPath();
	}

	public PageDefinition getPageDefinition(String uri) {
		//Note: we have to go thru UiFactory (so user can override it)
		uri = toAbsoluteURI(uri, false);
		final PageDefinition pagedef = ((WebAppCtrl)getDesktop().getWebApp()).
			getUiFactory().getPageDefinition(newRequestInfo(uri), uri);
		if (pagedef == null)
			throw new UiException("Page not found: "+uri);
		return pagedef;
	}
	public PageDefinition getPageDefinitionDirectly(String content, String ext) {
		//Note: we have to go thru UiFactory (so user can override it)
		return ((WebAppCtrl)getDesktop().getWebApp()).getUiFactory()
			.getPageDefinitionDirectly(newRequestInfo(null), content, ext);
	}
	public PageDefinition getPageDefinitionDirectly(Document content, String ext) {
		//Note: we have to go thru UiFactory (so user can override it)
		return ((WebAppCtrl)getDesktop().getWebApp()).getUiFactory()
			.getPageDefinitionDirectly(newRequestInfo(null), content, ext);
	}
	public PageDefinition getPageDefinitionDirectly(Reader reader, String ext)
	throws IOException {
		//Note: we have to go thru UiFactory (so user can override it)
		return ((WebAppCtrl)getDesktop().getWebApp()).getUiFactory()
			.getPageDefinitionDirectly(newRequestInfo(null), reader, ext);
	}
	private RequestInfo newRequestInfo(String uri) {
		final Desktop dt = getDesktop();
		return new RequestInfoImpl(
			dt, _request, PageDefinitions.getLocator(getDesktop().getWebApp(), uri));
	}

	public void setHeader(String name, String value) {
		if (_response instanceof HttpServletResponse)
			((HttpServletResponse)_response).setHeader(name, value);
	}

	public Object getRequestAttribute(String name) {
		return _request.getAttribute(name);
	}
	public void setRequestAttribute(String name, Object value) {
		_request.setAttribute(name, value);
	}

	public boolean isBrowser() {
		return true;
	}
	public boolean isRobot() {
		return Servlets.isRobot(_request);
	}
	public boolean isExplorer() {
		return Servlets.isExplorer(_request);
	}
	public boolean isExplorer7() {
		return Servlets.isExplorer7(_request);
	}
	public boolean isGecko() {
		return Servlets.isGecko(_request);
	}
	public boolean isSafari() {
		return Servlets.isSafari(_request);
	}
	public boolean isMilDevice() {
		return Servlets.isMilDevice(_request);
	}

	public Object getNativeRequest() {
		return _request;
	}
	public Object getNativeResponse() {
		return _response;
	}

	public Object getAttribute(String name) {
		return _request.getAttribute(name);
	}
	public void setAttribute(String name, Object value) {
		_request.setAttribute(name, value);
	}
	public void removeAttribute(String name) {
		_request.removeAttribute(name);
	}

	public Map getAttributes() {
		return _attrs;
	}

	private class ReqContext implements RequestContext {
		public Writer getOut() throws IOException {
			return _response.getWriter();
		}
		public VariableResolver getVariableResolver() {
			return ExecutionImpl.this.getVariableResolver();
		}
		public ServletRequest getRequest() {
			return _request;
		}
		public ServletResponse getResponse() {
			return _response;
		}
		public ServletContext getServletContext() {
			return _ctx;
		}
	}
	private class MyEval extends SimpleEvaluator { //not serializable
		private Page page;

		private MyEval(Page page, Class expfcls) {
			super(null, expfcls);
			this.page = page;
		}

		//super//
		public FunctionMapper getFunctionMapper(Object ref) {
			return page != null ? page.getFunctionMapper(): null;
		}
	}
}
