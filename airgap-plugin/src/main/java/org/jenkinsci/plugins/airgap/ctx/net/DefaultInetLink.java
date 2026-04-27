package org.jenkinsci.plugins.airgap.ctx.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.jenkinsci.plugins.airgap.IsolationContext.InetLink;

public class DefaultInetLink implements InetLink {

	private static final long serialVersionUID = 1L;

	protected final int prefix4;
	protected final int prefix6;

	protected final Inet4Address hostAddr4;
	protected final Inet6Address hostAddr6;
	protected final Inet4Address contextAddr4;
	protected final Inet6Address contextAddr6;

	public DefaultInetLink(//
			final int prefix4, final Inet4Address hostAddr4, final Inet4Address contextAddr4, //
			final int prefix6, final Inet6Address hostAddr6, final Inet6Address contextAddr6) {
		this.prefix4 = prefix4;
		this.hostAddr4 = hostAddr4;
		this.contextAddr4 = contextAddr4;
		this.prefix6 = prefix6;
		this.hostAddr6 = hostAddr6;
		this.contextAddr6 = contextAddr6;
	}

	@Override
	public int getNetworkPrefixV4() {
		return this.prefix4;
	}

	@Override
	public int getNetworkPrefixV6() {
		return this.prefix6;
	}

	@Override
	public Inet4Address getHostAddressV4() {
		return this.hostAddr4;
	}

	@Override
	public Inet6Address getHostAddressV6() {
		return this.hostAddr6;
	}

	@Override
	public Inet4Address getContextAddressV4() {
		return this.contextAddr4;
	}

	@Override
	public Inet6Address getContextAddressV6() {
		return this.contextAddr6;
	}

	public static DefaultInetLink createLoopback(final boolean ipv4, final boolean ipv6) {
		int prefix4 = -1;
		Inet4Address hostAddr4 = null, contextAddr4 = null;
		if (ipv4) {
			prefix4 = 32;
			try {
				hostAddr4 = contextAddr4 = (Inet4Address) InetAddress.getByAddress("localhost",
						new byte[] { 0x7f, 0x00, 0x00, 0x01 });
			} catch (UnknownHostException ignored) {
			}
		}
		int prefix6 = -1;
		Inet6Address hostAddr6 = null, contextAddr6 = null;
		if (ipv6) {
			prefix6 = 128;
			try {
				hostAddr6 = contextAddr6 = (Inet6Address) InetAddress.getByAddress("localhost", //
						new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, //
								0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 });
			} catch (UnknownHostException ignored) {
			}
		}
		return new DefaultInetLink(//
				prefix4, hostAddr4, contextAddr4, //
				prefix6, hostAddr6, contextAddr6);
	}
}