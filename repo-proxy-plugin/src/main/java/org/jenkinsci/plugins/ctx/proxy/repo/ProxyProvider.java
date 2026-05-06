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
package org.jenkinsci.plugins.ctx.proxy.repo;

import java.util.Set;
import org.jenkinsci.plugins.ctx.proxy.repo.config.ProxyConfig;
import org.jenkinsci.plugins.ctx.proxy.repo.config.ProxyConfigDescriptor;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public interface ProxyProvider extends ExtensionPoint {

	public boolean isApplicable(ProxyConfigDescriptor descriptor);

	public ProxyBootstrapper createBootstrapper(FlowNode flowNode, String refId, ProxyConfig config,
			Set<IpAddress<?>> availableIps);

	public static ExtensionList<ProxyProvider> all() {
		return ExtensionList.lookup(ProxyProvider.class);
	}
}
