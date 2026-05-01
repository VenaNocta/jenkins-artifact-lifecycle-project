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
package org.jenkinsci.plugins.ctx.proxy.repo.maven;

import java.util.concurrent.CompletableFuture;

import org.jenkinsci.plugins.ctx.proxy.repo.ProxyConfig;
import org.jenkinsci.plugins.ctx.proxy.repo.ProxyConfigDescriptor;
import org.jenkinsci.plugins.ctx.proxy.repo.ProxyInstance;
import org.jenkinsci.plugins.ctx.proxy.repo.ProxyProvider;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.Extension;
import jenkins.model.CauseOfInterruption;

@Extension
public class MavenProxyProvider implements ProxyProvider {

	@Override
	public boolean isApplicable(final ProxyConfigDescriptor descriptor) {
		return descriptor instanceof MvnProxyConfig.Descriptor;
	}

	@Override
	public ProxyInstance createInstance(final FlowNode flowNode, final String refId, final ProxyConfig config,
			final CompletableFuture<CauseOfInterruption> runFuture) {
		if (!(config instanceof MvnProxyConfig))
			return null;
		return new MvnProxyInstance();
	}
}
