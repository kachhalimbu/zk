/* DefinitionLoaders.java

{{IS_NOTE
	Purpose:
		
	Description:
		
	History:
		Mon Aug 29 21:57:08     2005, Created by tomyeh
}}IS_NOTE

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
package org.zkoss.zk.ui.metainfo;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.net.URL;
import java.io.IOException;

import org.zkoss.lang.D;
import org.zkoss.lang.Classes;
import org.zkoss.lang.Strings;
import org.zkoss.util.Utils;
import org.zkoss.util.IllegalSyntaxException;
import org.zkoss.util.logging.Log;
import org.zkoss.util.resource.Locator;
import org.zkoss.util.resource.ClassLocator;
import org.zkoss.idom.Document;
import org.zkoss.idom.Element;
import org.zkoss.idom.Attribute;
import org.zkoss.idom.ProcessingInstruction;
import org.zkoss.idom.input.SAXBuilder;
import org.zkoss.idom.util.IDOMs;
import org.zkoss.el.Taglib;
import org.zkoss.web.servlet.JavaScript;
import org.zkoss.web.servlet.StyleSheet;

import org.zkoss.zk.Version;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.metainfo.impl.*;
import org.zkoss.zk.scripting.Interpreters;
import org.zkoss.zk.device.Devices;

/**
 * Utilities to load language definitions.
 *
 * @author tomyeh
 */
public class DefinitionLoaders {
	private static final Log log = Log.lookup(DefinitionLoaders.class);

	private static final int MAX_VERSION_SEGMENT = 4;
	private static List _addons;
	/** A map of (String ext, String lang). */
	private static Map _exts;
	private static boolean _loaded, _loading;

	//CONSIDER:
	//Sotre language definitions per WebApp, since diff app may add its
	//own definitions thru WEB-INF's lang-addon, or a JAR in WEB-INF/lib.
	//
	//CONSEQUENCE:
	//Other Web app is affected by another in the current implementation
	//
	//WORKAROUND:
	//Copy ZK libraries into WEB-INF/lib
	//
	//DIFFICULTY TO SUPPORT
	//To support it we have to pass WebApp around. It is a challenge for
	//1) a working thread (other than servlet/event thread);
	//2) deserialize LanguageDefinition (and maybe ComponentDefinition)

	/** Adds a language addon.
	 */
	public static void addAddon(Locator locator, URL url) {
		if (locator == null || url == null)
			throw new IllegalArgumentException("null");

		if (_loaded) {
			loadAddon(locator, url);
		} else {
			if (_addons == null)
				_addons = new LinkedList();
			_addons.add(new Object[] {locator, url});
		}
	}
	/** Associates an extension to a language.
	 *
	 * @param lang the language name. It cannot be null.
	 * @param ext the extension, e.g., "svg". It cannot be null.
	 * @since 2.5.0
	 */
	public static final void addExtension(String ext, String lang) {
		if (_loaded) {
			LanguageDefinition.addExtension(ext, lang);
		} else {
			if (lang == null || ext == null)
				throw new IllegalArgumentException("null");
			if (_exts == null)
				_exts = new HashMap();
			_exts.put(ext, lang);
		}
	}

	/** Loads all config.xml, lang.xml and lang-addon.xml found in
	 * the /metainfo/zk path.
	 *
	 * <p>Remember to call {@link #addAddon}, if necessary, before
	 * calling this method.
	 */
	/*package*/ static void load() {
		if (!_loaded) {
			synchronized (DefinitionLoaders.class) {
				if (!_loaded && !_loading) {
					try {
						_loading = true; //prevent re-entry for the same thread
						load0();
					} finally {
						_loaded = true; //only once
						_loading = false;
					}
				}
			}
		}
	}
	private static void load0() {
		final ClassLocator locator = new ClassLocator();

		final int[] zkver = new int[MAX_VERSION_SEGMENT];
		for (int j = 0; j < MAX_VERSION_SEGMENT; ++j)
			zkver[j] = Utils.getSubversion(Version.UID, j);

		//1. process config.xml (no particular dependency)
		try {
			for (Enumeration en = locator.getResources("metainfo/zk/config.xml");
			en.hasMoreElements();) {
				final URL url = (URL)en.nextElement();
				if (log.debugable()) log.debug("Loading "+url);
				try {
					final Document doc = new SAXBuilder(false, false, true).build(url);
					if (checkVersion(zkver, url, doc))
						parseConfig(doc.getRootElement());
				} catch (Exception ex) {
					throw UiException.Aide.wrap(ex, "Failed to load "+url);
						//abort since it is hardly to work then
				}
			}
		} catch (Exception ex) {
			throw UiException.Aide.wrap(ex); //abort
		}

		//2. process lang.xml (no particular dependency)
		try {
			for (Enumeration en = locator.getResources("metainfo/zk/lang.xml");
			en.hasMoreElements();) {
				final URL url = (URL)en.nextElement();
				if (log.debugable()) log.debug("Loading "+url);
				try {
					final Document doc = new SAXBuilder(false, false, true).build(url);
					if (checkVersion(zkver, url, doc))
						parseLang(doc, locator, false);
				} catch (Exception ex) {
					throw UiException.Aide.wrap(ex, "Failed to load "+url);
						//abort since it is hardly to work then
				}
			}
		} catch (Exception ex) {
			throw UiException.Aide.wrap(ex); //abort
		}

		//3. process lang-addon.xml (with dependency)
		try {
			final List xmls = locator.getDependentXMLResources(
				"metainfo/zk/lang-addon.xml", "addon-name", "depends");
			for (Iterator it = xmls.iterator(); it.hasNext();) {
				final ClassLocator.Resource res = (ClassLocator.Resource)it.next();
				try {
					if (checkVersion(zkver, res.url, res.document))
						parseLang(res.document, locator, true);
				} catch (Exception ex) {
					log.error("Failed to load addon", ex);
					//keep running
				}
			}
		} catch (Exception ex) {
			log.error("Failed to load addon", ex);
			//keep running
		}

		//4. process other addon (from addAddon)
		if (_addons != null) {
			for (Iterator it = _addons.iterator(); it.hasNext();) {
				final Object[] p = (Object[])it.next();
				loadAddon((Locator)p[0], (URL)p[1]);
			}
			_addons = null; //free memory
		}

		//5. process the extension
		if (_exts != null) {
			for (Iterator it = _exts.entrySet().iterator(); it.hasNext();) {
				final Map.Entry me = (Map.Entry)it.next();
				LanguageDefinition.addExtension((String)me.getKey(),
					(String)me.getValue());
			}
			_exts = null;
		}
	}
	/** Loads a language addon.
	 */
	private static void loadAddon(Locator locator, URL url) {
		try {
			parseLang(
				new SAXBuilder(false, false, true).build(url), locator, true);
		} catch (Exception ex) {
			log.error("Failed to load addon: "+url, ex);
			//keep running
		}
	}

	/** Checks and returns whether the loaded document's version is correct.
	 */
	private static boolean checkVersion(int[] zkver, URL url, Document doc)
	throws Exception {
		final Element el = doc.getRootElement().getElement("version");
		if (el != null) {
			final String reqzkver = el.getElementValue("zk-version", true);
			if (reqzkver != null) {
				for (int j = 0; j < MAX_VERSION_SEGMENT; ++j) {
					int v = Utils.getSubversion(reqzkver, j);
					if (v < zkver[j]) break; //ok
					if (v > zkver[j]) {//failed
						log.info("Ignore "+url+"\nCause: ZK version must be "+zkver+" or later, not "+Version.UID);
						return false;
					}
				}
			}

			final String clsnm = IDOMs.getRequiredElementValue(el, "version-class");
			final String uid = IDOMs.getRequiredElementValue(el, "version-uid");
			final Class cls = Classes.forNameByThread(clsnm);
			final Field fld = cls.getField("UID");
			final String uidInClass = (String)fld.get(null);
			if (uid.equals(uidInClass)) {
				return true;
			} else {
				log.info("Ignore "+url+"\nCause: version not matched; expected="+uidInClass+", xml="+uid);
				return false;
			}
		} else {
			log.info("Ignore "+url+"\nCause: version not specified");
			return false; //backward compatible
		}
	}

	private static void parseConfig(Element el)
	throws Exception {
		parseZScriptConfig(el);
		parseDeviceConfig(el);
	}
	private static void parseZScriptConfig(Element root) {
		for (Iterator it = root.getElements("zscript-config").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			Interpreters.add(el);
				//Note: zscript-config is applied to the whole system, not just langdef
		}
	}
	private static void parseDeviceConfig(Element root) {
		for (Iterator it = root.getElements("device-config").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			Devices.add(el);
		}
	}

	private static void parseLang(Document doc, Locator locator, boolean addon)
	throws Exception {
		final Element root = doc.getRootElement();
		final String lang = IDOMs.getRequiredElementValue(root, "language-name");
		final LanguageDefinition langdef;
		if (addon) {
			if (log.debugable()) log.debug("Addon language to "+lang+" from "+root.getElementValue("addon-name", true));
			langdef = LanguageDefinition.lookup(lang);

			if (root.getElement("case-insensitive") != null)
				throw new UiException("case-insensitive not allowed in addon");
		} else {
			final String ns =
				(String)IDOMs.getRequiredElementValue(root, "namespace");
			final String deviceType =
				(String)IDOMs.getRequiredElementValue(root, "device-type");

			//if (log.debugable()) log.debug("Load language: "+lang+", "+ns);

			final Map pagemolds = parseMolds(root);
			final String desktopURI = (String)pagemolds.get("desktop");
			final String pageURI = (String)pagemolds.get("page");
			if (desktopURI == null || pageURI == null)
				throw new IllegalSyntaxException("Both desktop and page molds must be specified, "+root.getLocator());

			final List exts = parseExtensions(root);
			if (exts.isEmpty())
				throw new IllegalSyntaxException("The extension must be specified for "+lang);

			String ignoreCase = root.getElementValue("case-insensitive", true);
			String bNative = root.getElementValue("native-namespace", true);

			langdef = new LanguageDefinition(
				deviceType, lang, ns, exts, desktopURI, pageURI,
				"true".equals(ignoreCase), "true".equals(bNative), locator);
		}

		parsePI(langdef, doc);
		parseLabelTemplate(langdef, root);
		parseDynamicTag(langdef, root);
		parseMacroTemplate(langdef, root);
		parseNativeTemplate(langdef, root);

		for (Iterator it = root.getElements("javascript").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			final String src = el.getAttributeValue("src");
			final String ctn = el.getText(true);
			final JavaScript js;
			if (src != null && src.length() > 0) {
				if (ctn != null && ctn.length() > 0)
					throw new UiException("You cannot specify the content if the src attribute is specified, "+el.getLocator());
				final String charset = el.getAttributeValue("charset");
				js = new JavaScript(src, charset);
			} else if (ctn != null && ctn.length() > 0) {
				js = new JavaScript(ctn);
			} else {
				throw new UiException("You must specify either the src attribute or the content, "+el.getLocator());
			}
			langdef.addJavaScript(js);
		}
		for (Iterator it = root.getElements("javascript-module").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			langdef.addJavaScriptModule(
				IDOMs.getRequiredAttributeValue(el, "name"),
				IDOMs.getRequiredAttributeValue(el, "version"));
		}


		for (Iterator it = root.getElements("stylesheet").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			final String href = el.getAttributeValue("href");
			final String ctn = el.getText(true);
			final StyleSheet ss;
			if (href != null && href.length() > 0) {
				if (ctn != null && ctn.length() > 0)
					throw new UiException("You cannot specify the content if the href attribute is specified, "+el.getLocator());
				ss = new StyleSheet(href, el.getAttributeValue("type"));
			} else if (ctn != null && ctn.length() > 0) {
				ss = new StyleSheet(ctn, el.getAttributeValue("type"), true);
			} else {
				throw new UiException("You must specify either the href attribute or the content, "+el.getLocator());
			}
			langdef.addStyleSheet(ss);
		}

		for (Iterator it = root.getElements("zscript").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			final String zslang;
			final Attribute attr = el.getAttributeItem("language");
			if (attr == null) {
				zslang = "Java";
			} else {
				zslang = attr.getValue();
				if (zslang == null || zslang.length() == 0)
					throw new UiException("The language attribute cannot be empty, "+attr.getLocator());
			}
			final String s = el.getText(true);
			final String eachTime = el.getAttributeValue("each-time");
			if ("true".equals(eachTime))
				langdef.addEachTimeScript(zslang, s);
			else
				langdef.addInitScript(zslang, s);
		}

		for (Iterator it = root.getElements("component").iterator();
		it.hasNext();) {
			final Element el = (Element)it.next();
			final String name =
				IDOMs.getRequiredElementValue(el, "component-name");

			final String macroUri = el.getElementValue("macro-uri", true);
			final ComponentDefinitionImpl compdef;
			if (macroUri != null && macroUri.length() != 0) {
				if (log.finerable()) log.finer("macro component definition: "+name);

				final String inline = el.getElementValue("inline", true);
				compdef = (ComponentDefinitionImpl)
					langdef.getMacroDefinition(
						name, macroUri, "true".equals(inline), false);

				final String clsnm = el.getElementValue("component-class", true);
				if (clsnm != null && clsnm.length() > 0) {
					noEL("component-class", clsnm, el);
					compdef.setImplementationClass(locateClass(clsnm));
						//resolve it now because it is part of lang-addon
				}

				langdef.addComponentDefinition(compdef);
			} else if (el.getElement("extends") != null) { //override
				if (log.finerable()) log.finer("Override component definition: "+name);

				final String extnm = el.getElementValue("extends", true);
				final ComponentDefinition ref = langdef.getComponentDefinition(extnm);
				if (ref.isMacro())
					throw new UiException("Unable to extend from a macro component, "+el.getLocator());

				if (extnm.equals(name)) {
					compdef = (ComponentDefinitionImpl)ref;
				} else {
					compdef = (ComponentDefinitionImpl)
						ref.clone(ref.getLanguageDefinition(), name);
					langdef.addComponentDefinition(compdef);
				}

				final String clsnm = el.getElementValue("component-class", true);
				if (clsnm != null && clsnm.length() > 0) {
					noEL("component-class", clsnm, el);
					compdef.setImplementationClass(locateClass(clsnm));
				}
			} else {
				if (log.finerable()) log.finer("Add component definition: name="+name);

				final String clsnm =
					IDOMs.getRequiredElementValue(el, "component-class");
				noEL("component-class", clsnm, el);
				compdef = new ComponentDefinitionImpl(
					langdef, name, locateClass(clsnm));
				langdef.addComponentDefinition(compdef);
			}

			final String textAs = el.getElementValue("text-as", true);
			if (textAs != null) { //empty means cleanup (for overriding)
				noEL("text-as", textAs, el);
				compdef.setTextAs(textAs);
			}

			for (Iterator e = parseMolds(el).entrySet().iterator(); e.hasNext();) {
				final Map.Entry me = (Map.Entry)e.next();
				compdef.addMold((String)me.getKey(), (String)me.getValue());
			}

			for (Iterator e = parseCustAttrs(el).entrySet().iterator(); e.hasNext();) {
				final Map.Entry me = (Map.Entry)e.next();
				compdef.addCustomAttribute((String)me.getKey(), (String)me.getValue());
			}

			for (Iterator e = parseProps(el).entrySet().iterator(); e.hasNext();) {
				final Map.Entry me = (Map.Entry)e.next();
				compdef.addProperty((String)me.getKey(), (String)me.getValue());
			}

			parseAnnots(compdef, el);
		}
	}
	private static Class locateClass(String clsnm) throws Exception {
		try {
			return Classes.forNameByThread(clsnm);
		} catch (ClassNotFoundException ex) {
			throw new ClassNotFoundException("Not found: "+clsnm, ex);
		}
	}
	private static void noEL(String nm, String val, Element el)
	throws UiException {
		if (val != null && val.indexOf("${") >= 0)
			throw new UiException(nm+" does not support EL expressions, "+el.getLocator());
	}
	/** Parse the processing instructions. */
	private static void parsePI(LanguageDefinition langdef, Document doc)
	throws Exception {
		for (Iterator it = doc.getChildren().iterator(); it.hasNext();) {
			final Object o = it.next();
			if (!(o instanceof ProcessingInstruction))
				continue;

			final ProcessingInstruction pi = (ProcessingInstruction)o;
			final String target = pi.getTarget();
			final Map params = pi.parseData();
			if ("taglib".equals(target)) {
				final String uri = (String)params.remove("uri");
				final String prefix = (String)params.remove("prefix");
				if (!params.isEmpty())
					log.warning("Ignored unknown attribute: "+params+", "+pi.getLocator());
				if (uri == null || prefix == null)
					throw new IllegalSyntaxException("Both uri and prefix attribute are required, "+pi.getLocator());
				if (log.debugable()) log.debug("taglib: prefix="+prefix+" uri="+uri);
				langdef.addTaglib(new Taglib(prefix, uri));
			} else {
				log.warning("Unknown processing instruction: "+target);
			}
		}
	}
	/** Parse the component used to represent a label.
	 */
	private static
	void parseLabelTemplate(LanguageDefinition langdef, Element el) {
		el = el.getElement("label-template");
		if (el != null) {
			final Element raw = el.getElement("raw");
			langdef.setLabelTemplate(
				IDOMs.getRequiredElementValue(el, "component-name"),
				IDOMs.getRequiredElementValue(el, "component-attribute"),
				raw != null && !"false".equals(raw.getText(true)));
		}
	}
	private static
	void parseMacroTemplate(LanguageDefinition langdef, Element el)
	throws Exception {
		el = el.getElement("macro-template");
		if (el != null) {
			langdef.setMacroTemplate(
				locateClass(IDOMs.getRequiredElementValue(el, "macro-class")),
				IDOMs.getRequiredElementValue(el, "macro-uri"));
		}
	}
	private static
	void parseNativeTemplate(LanguageDefinition langdef, Element el)
	throws Exception {
		el = el.getElement("native-template");
		if (el != null) {
			langdef.setNativeTemplate(
				locateClass(IDOMs.getRequiredElementValue(el, "native-class")));
		}
	}
	private static
	void parseDynamicTag(LanguageDefinition langdef, Element el)
	throws ClassNotFoundException {
		el = el.getElement("dynamic-tag");
		if (el != null) {
			final String compnm =
				IDOMs.getRequiredElementValue(el, "component-name");
			final Set reservedAttrs = new HashSet(5);
			for (Iterator it = el.getElements("reserved-attribute").iterator();
			it.hasNext();)
				reservedAttrs.add(((Element)it.next()).getText(true));
			langdef.setDynamicTagInfo(compnm, reservedAttrs);
		}
		//if (log.finerable()) log.finer(el);
	}
	private static List parseExtensions(Element elm) {
		final List exts = new LinkedList();
		for (Iterator it = elm.getElements("extension").iterator(); it.hasNext();) {
			final Element el = (Element)it.next();
			final String ext = el.getText(true);
			if (ext.length() != 0) {
				for (int j = 0, len = ext.length(); j < len; ++j) {
					final char cc = ext.charAt(j);
					if ((cc < 'a' || cc > 'z') && (cc < 'A' || cc > 'Z')
					&& (cc < '0' || cc > '9'))
						throw new UiException("Invalid extension; only letters and numbers are allowed: "+ext);
				}
				exts.add(ext);
			}
		}
		///if (log.finerable()) log.finer(exts);
		return exts;
	}
	private static Map parseProps(Element elm) {
		return IDOMs.parseParams(elm, "property", "property-name", "property-value");
	}
	private static Map parseMolds(Element elm) {
		return IDOMs.parseParams(elm, "mold", "mold-name", "mold-uri");
	}
	private static Map parseCustAttrs(Element elm) {
		return IDOMs.parseParams(elm, "custom-attribute", "attribute-name", "attribute-value");
	}
	private static Map parseAttrs(Element elm) {
		return IDOMs.parseParams(elm, "attribute", "attribute-name", "attribute-value");
	}

	private static void parseAnnots(ComponentDefinitionImpl compdef, Element top) {
		for (Iterator it = top.getElements("annotation").iterator(); it.hasNext();) {
			final Element el = (Element)it.next();
			final String annotName = IDOMs.getRequiredElementValue(el, "annotation-name");
			final Map annotAttrs = parseAttrs(el);
			final String prop = el.getElementValue("property-name", true);
			if (prop == null || prop.length() == 0)
				compdef.addAnnotation(annotName, annotAttrs);
			else
				compdef.addAnnotation(prop, annotName, annotAttrs);
		}
	}

	private static class Addon {
		private final Document document;
		private final int priority;
		private Addon(Document document) {
			this.document = document;

			final String p = document.getRootElement().getElementValue("priority", true);
			this.priority = p != null && p.length() > 0 ? Integer.parseInt(p): 0;
		}
		private static void add(List addons, Document document) {
			final Addon addon = new Addon(document);
			for (ListIterator it = addons.listIterator(); it.hasNext();) {
				final Addon a = (Addon)it.next();
				if (a.priority < addon.priority) {
					it.previous();
					it.add(addon);
					return; //done
				}
			}
			addons.add(addon);
		}
	}
}
