/* PageCtrl.java

{{IS_NOTE
	Purpose:
		
	Description:
		
	History:
		Wed Jun  8 13:55:09     2005, Created by tomyeh
}}IS_NOTE

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.ui.sys;

import java.util.Collection;
import java.io.Writer;
import java.io.IOException;

import javax.servlet.jsp.el.FunctionMapper;

import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.metainfo.ZScript;

/**
 * Addition interface to {@link org.zkoss.zk.ui.Page} for implementation
 * purpose.
 *
 * <p>Application developers shall never access any of this methods.
 *
 * @author tomyeh
 */
public interface PageCtrl {
	/** The execution attribute used to control {@link #redraw} to use
	 * include instead of forward to redraw the page
	 */
	public static final String ATTR_REDRAW_BY_INCLUDE = "org.zkoss.zk.ui.redrawByInclude";

	/** Initializes this page by assigning the info provided by
	 * the specified {@link PageConfig}, and then adds it
	 * to a desktop (by use of {@link Execution#getDesktop}).
	 *
	 * <p>This method shall be called only after the current execution
	 * is activated.
	 *
	 * @param config the info about how to initialize this page
	 * @since 2.5.0
	 */
	public void init(PageConfig config);
	/** Called when this page is about to be detroyed.
	 * It is called by desktop, after removing it from the desktop.
	 */
	public void destroy();

	/** Returns the header elements declared in this page (never null).
	 * An empty string is returned if special header is defined.
	 * <p>For HTML, the header element is the HEAD element.
	 */
	public String getHeaders();
	/** Returns the attributes of the root element declared in this page
	 * (never null).
	 * An empty string is returned if no special attribute is declared.
	 * <p>For HTML, the root element is the HTML element.
	 * @since 2.5.0
	 */
	public String getRootAttributes();
	/** Returns the doc type, or null to use the device default.
	 * @since 2.5.0
	 */
	public String getDocType();
	/** Returns the content type, or null to use the device default.
	 * @since 2.5.0
	 */
	public String getContentType();

	/** Resolves a variable thru all registered variable resolvers
	 * ({@link org.zkoss.zk.scripting.VariableResolver}).
	 *
	 * <p>You rarely need to call this method, since
	 * it is called implicitly by {@link org.zkoss.zk.ui.Page#getVariable}.
	 *
	 * @see org.zkoss.zk.ui.Page#addVariableResolver
	 */
	//deprecated
	//public Object resolveVariable(String name);

	/** Returns the owner of this page, or null if it is not owned by
	 * any component.
	 * A page is included by a component. We say it is owned by the component.
	 */
	public Component getOwner();
	/** Sets the owner of this page.
	 * <p>Used only internally.
	 */
	public void setOwner(Component comp);

	/** Redraws the whole page into the specified output.
	 *
	 * <p>You could use {@link #ATTR_REDRAW_BY_INCLUDE} to control
	 * whether to include, instead of forward, the page content.
	 * By default, {@link Execution#forward } is used if possible.
	 *
	 * @param responses a list of responses that the page has to generate
	 * corresponding javascript to process them; or null if no such responses.
	 * The responses is not null, if and only if the page is creating
	 */
	public void redraw(Collection responses, Writer out) throws IOException;

	//-- Used for component implementation --//
	/** Adds a root component to a page.
	 * <p>It is used internally and developers shall not invoke it
	 * explicityly.
	 * @see Component#setPage
	 */
	public void addRoot(Component comp);
	/** Detaches a root component from this page.
	 * <p>It is used internally and developers shall not invoke it
	 * explicitly
	 * @see Component#setPage
	 */
	public void removeRoot(Component comp);
	/** Moves a root component before the reference component.
	 *
	 * <p>Note: it assumes removeRoot was called before for comp.
	 * Otherwise, nothing happens.
	 *
	 * <p>It is used internally and developers shall not invoke it
	 * explicitly
	 *
	 * @since 2.5.0
	 * @see Component#setPageBefore
	 */
	public void moveRoot(Component comp, Component refRoot);

	/** Adds a fellow. */
	public void addFellow(Component comp);
	/** Removes a fellow. */
	public void removeFellow(Component comp);
	/** Returns whether a fellow exists with the specified component ID.
	 */
	public boolean hasFellow(String compId);

	/** Adds a deferred zscript.
	 *
	 * @param parent the component that is the parent of zscript (in
	 * the ZUML page), or null if it belongs to the page.
	 * @param zscript the zscript that shall be evaluated as late as
	 * when the interpreter of the same language is being loaded.
	 */
	public void addDeferredZScript(Component parent, ZScript zscript);
 	/** Returns the default parent, or null if no such parent.
 	 * If a default parent is defined (by use of {@link #setDefaultParent}),
 	 * {@link org.zkoss.zk.ui.Executions#createComponents(String, Component, java.util.Map)} will
 	 * use it as the default parent, if developers didn't specify one.
 	 */
 	public Component getDefaultParent();
 	/** Sets the default parent.
 	 *
 	 * <p>It is rarely used by application developers. Rather, it is used
 	 * by ZHTML's body to make sure new created compnents are placed
 	 * correctly.
 	 *
 	 * <p>Caller has to ensure the comp is part of the page. Otherwise,
 	 * the result is unpreditable.
 	 *
 	 * @see #getDefaultParent
 	 */
 	public void setDefaultParent(Component comp);

	/** Notification that the session, which owns this page,
	 * is about to be passivated (aka., serialized).
	 */
	public void sessionWillPassivate(Desktop desktop);
	/** Notification that the session, which owns this page,
	 * has just been activated (aka., deserialized).
	 */
	public void sessionDidActivate(Desktop desktop);
}
