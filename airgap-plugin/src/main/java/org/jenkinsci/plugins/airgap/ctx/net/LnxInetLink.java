package org.jenkinsci.plugins.airgap.ctx.net;

import java.net.Inet4Address;
import java.net.Inet6Address;

public class LnxInetLink extends DefaultInetLink {

	private static final long serialVersionUID = 1L;

	protected final String hostIfaceName;
	protected final String contextIfaceName;

	public LnxInetLink(final String hostIfaceName, final String contextIfaceName, //
			final Inet4Address hostAddr4, final Inet4Address contextAddr4, //
			final Inet6Address hostAddr6, final Inet6Address contextAddr6) {
		super(//
				30, hostAddr4, contextAddr4, //
				126, hostAddr6, contextAddr6);
		this.hostIfaceName = hostIfaceName;
		this.contextIfaceName = contextIfaceName;
	}

	public String getHostIfaceName() {
		return this.hostIfaceName;
	}

	public String getContextIfaceName() {
		return this.contextIfaceName;
	}
}