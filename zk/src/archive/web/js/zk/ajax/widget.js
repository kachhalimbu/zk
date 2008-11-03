/* widget.js

{{IS_NOTE
	Purpose:
		Widget - the UI object at the client
	Description:
		
	History:
		Tue Sep 30 09:23:56     2008, Created by tomyeh
}}IS_NOTE

Copyright (C) 2008 Potix Corporation. All Rights Reserved.

{{IS_RIGHT
	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
}}IS_RIGHT
*/
zk.Widget = zk.$extends(zk.Object, {
	/** The UUID (readonly if inServer). */
	//uuid: null,
	/** The next sibling (readonly). */
	//nextSibling: null,
	/** The previous sibling widget (readonly). */
	//previousSibling: null,
	/** The parent (readonly).
	 */
	//parent: null,
	/** The first child widget (readonly). */
	//firstChild: null,
	/** The last child widget (readonly). */
	//lastChild: null,
	/** The page that this widget belongs to (readonly). */
	//page: null,

	/** Whether this widget has a copy at the server (readonly). */
	//inServer: false,

	/** Constructor. */
	construct: function (uuid, mold) {
		this.uuid = uuid ? uuid: zk.Widget.nextUuid();
		this.mold = mold ? mold: "default";
	},
	/** Generates the HTML content. */
	redraw: function () {
		var s = this.$class.molds[this.mold].call(this);
		return this.prolog ? this.prolog + s: s;
	},
	/** Appends a child widget.
	 */
	appendChild: function (child) {
		if (child == this.lastChild)
			return;

		if (child.parent)
			child.parent.removeChild(child);

		child.parent = this;
		var p = this.lastChild;
		if (p) {
			p.nextSibling = child;
			child.previousSibling = p;
			this.lastChild = child;
		} else {
			this.firstChild = this.lastChild = child;
		}

		//TODO: if parent belongs to DOM
	},
	/** Inserts a child widget before the specified one.
	 */
	insertBefore: function (child, sibling) {
		if (!sibling || sibling.parent != this) {
			this.appendChild(child);
			return;
		}

		if (child == sibling || child.nextSibling == sibling)
			return;

		if (child.parent)
			child.parent.removeChild(child);

		var p = sibling.previousSibling;
		if (p) {
			child.previousSibling = p;
			p.nextSibling = child;
		} else this.firstChild = child;

		sibling.previousSibling = child;
		child.nextSibling = sibling;

		child.desktop = this.desktop;

		//TODO: if parent belongs to DOM
	},
	/** Removes the specified child.
	 */
	removeChild: function (child) {
		if (!child.parent)
			return;
		if (this != child.parent)
			throw "Not a child: "+child;

		var p = child.previousSibling, n = child.nextSibling;
		if (p) p.nextSibling = n;
		else this.firstChild = n;
		if (n) n.previousSibling = p;
		else this.lastChild = p;
		child.nextSibling = child.previousSibling = child.parent = null;

		child.desktop = null;

		//TODO: if parent belongs to DOM
	},

	/** Replaces the specified DOM element with this widget.
	 * @param desktop the desktop the DOM element belongs to.
	 * Optional. If null, ZK will decide it automatically.
	 */
	replace: function (n, desktop) {
		if (n.z_wgt) n.z_wgt.detach(); //detach first
		zDom.setOuterHTML(zDom.$(n), this.redraw());
		this.attach(desktop);
	},
	/** Attaches the widget to the DOM tree.
	 * @param desktop the desktop the DOM element belongs to.
	 * Optional. If null, ZK will decide it automatically.
	 */
	attach: function (desktop) {
		var n = zDom.$(this.uuid);
		if (n) {
			n.z_wgt = this;
			this.node = n;
			if (!desktop) desktop = zk.Desktop.ofNode(n);
			this.desktop = desktop;
		}
		for (var wgt = this.firstChild; wgt; wgt = wgt.nextSibling)
			wgt.attach(desktop);
	},
	/** Detaches the widget from the DOM tree.
	 */
	detach: function () {
		//TOD
	},

	//ZK event//
	/** An array of important events. An import event is an event
	 * that must be sent to the server even without event listener.
	 * It is usually about state-updating, such as onChange and onSelect.
	 * <p>Default: null if no event at all.
	 */
	//importantEvents: null,
	/** Fires a Widget event.
	 * Note: the event will be sent to the server if it is in server
	 * (@{link #inServer}), and belongs to a desktop.
	 *
	 * @param evt an instance of zk.Event
	 * @param timeout the delay before sending the non-deferrable AU request
	 * (if necessary).
	 * If not specified or negative, it is decided automatically.
	 * It is ignored if no non-deferrable listener is registered at the server.
	 */
	fire: function (evt, timeout) {
		var lsns = this._lsns[name],
			len = lsns ? lsns.length: 0;
		if (len) {
			for (var j = 0; j < len;) {
				var o = lsns[j++];
				o[name].apply(o, evt);
				if (evt.stop) return; //no more processing
			}
		}

		if (this.inServer && this.desktop) {
			var ies = this.importantEvents,
				evtnm = evt.name,
				asap = this[evtnm];
			if (asap != zk.undefined
			|| (ies != null && ies.contains(evtnm)))
				zAu.send(evt,
					asap ? timeout >= 0 ? timeout: 38: this.auDelay());
		}
	},
	/** A simpler way to fire an event. */
	fire2: function (evtnm, data, implicit, ignorable) {
		this.fire(new zk.Event(this, evtnm, data, implicit, ignorable));
	},
	/** Adds a listener to the specified event.
	 * The listener must have a method having the same name as the event.
	 * For example, if wgt.listen("onChange", lsn) is called, then
	 * lsn.onChange(evt) will be called when onChange event is fired
	 * (by {@link zk.Widget#fire}.
	 * @param overwrite whether to overwrite if the watch was added.
	 * @return true if added successfully.
	 */
	listen: function (evtnm, listener, overwrite) {
		var lsns = this._lsns[name];
		if (!lsns) lsns = this._lsns[name] = [];
		lsns.add(listener, overwrite);
	},
	/** Removes a listener from the sepcified event.
	 */
	unlisten: function (evtnm, listener) {
		var lsns = this._lsns[name];
		return lsns && lsns.remove(watch);
	},
	_lsns: {}, //listeners Map(evtnm,listener)
	/** Returns the delay before sending a deferrable event.
	 * <p>Default: -1.
	 */
	auDelay: function () {
		return -1;
	},
	/** Sets an attribute that is caused by an AU response (smartUpdate).
	 */
	setAttr: function (nm, val) {
	},
	/** Removes an attribute that is caused by an AU response (smartUpdate)
	 */
	rmAttr: function (nm, val) {
	}
}, {
	/** Returns the widget of the specified ID, or null if not found,
	 * or the widget is attached to the DOM tree.
	 * <p>Note: null is returned if the widget is not attached to the DOM tree
	 * (i.e., not associated with an DOM element).
	 */
	$: function (uuid) {
		//No map from uuid to widget directly. rather, go thru DOM
		var n = zDom.$(uuid);
		for (; n; n = n.parentNode) {
			var wgt = n.z_wgt;
			if (wgt) return wgt;
		}
		return null;
	},

	/** Returns the next unquie widget UUID.
	 */
	nextUuid: function () {
		return "_z_" + this._nextUuid++;
	},
	_nextUuid: 0
});

/** A ZK desktop. */
zk.Desktop = zk.$extends(zk.Object, {
	/** The type (always "#d")(readonly). */
	type: "#d",
	/** The AU request that shall be sent. Used by au.js */
	_aureqs: [],

	construct: function (dtid, updateURI) {
		var zdt = zk.Desktop, dt = zdt.all[dtid];
		if (!dt) {
			this.id = dtid;
			this.updateURI = updateURI;
			zdt.all[dtid] = this;
			++zdt._ndt;
			if (!zdt._dt) zdt._dt = this; //default desktop
		} else if (updateURI)
			dt.updateURI = updateURI;

		zdt.cleanup();
	}
},{
	/** Returns the desktop of the specified desktop ID.
	 */
	$: function (dtid) {
		return dtid ? typeof dtid == 'string' ?
			zk.Desktop.all[dtid]: dtid: zk.Desktop._dt;
	},
	/** Returns the desktop that the specified element belongs to.
	 */
	ofNode: function (n) {
		var zdt = zk.Desktop, dts = zdt.all;
		if (zdt._ndt > 1) {
			var wgt = zk.Widget.$(n);
			if (wgt)
				for (; wgt; wgt = wgt.parent)
					if (wgt.desktop)
						return wgt.desktop;
		}
		if (zdt._dt) return zdt._dt;
		for (var dtid in dts)
			return dts[dtid];
	},
	/** A map of (String dtid, zk.Desktop dt) (readonly). */
	all: {},
	_ndt: 0,
	/** Remove desktops that are no longer valid.
	 */
	cleanup: function () {
		var zdt = zk.Desktop, dts = zdt.all;
		if (zdt._dt && zdt._dt.pgid && !zDom.$(zdt._dt.pgid)) //removed
			zdt._dt = null;
		for (var dtid in dts) {
			var dt = dts[dtid];
			if (dt.pgid && !zDom.$(dt.pgid)) { //removed
				delete dts[dtid];
				--zdt._ndt;
			} else if (!zdt._dt)
				zdt._dt = dt;
		}
	}
});

/** A ZK page. */
zk.Page = zk.$extends(zk.Widget, {//unlik server, we derive from Widget!
	/** The type (always "#p")(readonly). */
	type: "#p",
	/** The style (readonly). */
	style: "width:100%;height:100%",
	construct: function (pgid, contained) {
		this.uuid = pgid;
		var n = this.node = zDom.$(pgid); //might null
		if (n) n.z_wgt = this;
		if (contained)
			zk.Page.contained.add(this, true);
	},
	redraw: function () {
		var html = '<div id="' + this.uuid + '" style="' + this.style + '">';
		for (var w = this.firstChild; w; w = w.nextSibling)
			html += w.redraw();
		return html + '</div>';
	}

},{
	/** An list of contained page (i.e., standalone but not covering
	 * the whole browser window.
	 */
	contained: []
});
