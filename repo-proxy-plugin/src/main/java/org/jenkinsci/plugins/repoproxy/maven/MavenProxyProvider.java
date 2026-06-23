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
package org.jenkinsci.plugins.repoproxy.maven;

import java.util.Set;

import org.jenkinsci.plugins.repoproxy.ProxyBootstrapper;
import org.jenkinsci.plugins.repoproxy.ProxyProvider;
import org.jenkinsci.plugins.repoproxy.config.ProxyConfig;
import org.jenkinsci.plugins.repoproxy.config.ProxyConfigDescriptor;
import org.jenkinsci.plugins.repoproxy.maven.config.MvnProxyConfig;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.Extension;
import net.runeduniverse.lib.utils.net.api.IpAddress;

@Extension
public class MavenProxyProvider implements ProxyProvider {

	@Override
	public boolean isApplicable(final ProxyConfigDescriptor descriptor) {
		return descriptor instanceof MvnProxyConfig.Descriptor;
	}

	@Override
	public ProxyBootstrapper createBootstrapper(final FlowNode flowNode, final String refKey, final ProxyConfig config,
			final Set<IpAddress<?>> availableIps) {
		if (!(config instanceof MvnProxyConfig))
			return null;

		MvnProxyBootstrapper instance = flowNode.getAction(MvnProxyBootstrapper.class);
		if (instance == null) {
			flowNode.addAction(instance = new MvnProxyBootstrapper(availableIps, 8000));
		} else if (!instance.canSupport(refKey, availableIps, 8000))
			return null;

		instance.addRepoInstance(refKey, (MvnProxyConfig) config);

		return instance;
	}
}
