/*
 * Copyright © 2026 VenaNocta (venanocta@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.airgap.ctx.net;

import net.runeduniverse.lib.utils.net.api.Ip4Address;
import net.runeduniverse.lib.utils.net.api.Ip6Address;
import net.runeduniverse.lib.utils.net.api.IpNet4Address;
import net.runeduniverse.lib.utils.net.api.IpNet6Address;

public class LnxNetLink extends DefaultNetLink {

	private static final long serialVersionUID = 1L;

	protected final String hostIfaceName;
	protected final String contextIfaceName;

	public LnxNetLink(final String hostIfaceName, final String contextIfaceName, //
			final IpNet4Address subnet4, final Ip4Address hostAddr4, final Ip4Address contextAddr4, //
			final IpNet6Address subnet6, final Ip6Address hostAddr6, final Ip6Address contextAddr6) {
		super( //
				subnet4, hostAddr4, contextAddr4, //
				subnet6, hostAddr6, contextAddr6);
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