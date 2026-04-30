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
package org.jenkinsci.plugins.ctx.isolation.hooks;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.ctx.isolation.config.NodeIsolationRuntimeAction;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;

@Extension
public class IsolationContextComputerListener extends ComputerListener {

	public static final String ENV_ISOLATION_SUBNET4 = "ISOLATION_SUBNET_V4";
	public static final String ENV_ISOLATION_SUBNET6 = "ISOLATION_SUBNET_V6";

	@Override
	public void onOnline(final Computer computer, final TaskListener listener)
			throws IOException, InterruptedException {

		final EnvVars env = computer.getEnvironment();
		final Node node = computer.getNode();

		final NodeIsolationRuntimeAction runtime = NodeIsolationRuntimeAction.get(node);
		if (runtime == null)
			return;

		String subnet = StringUtils.trimToNull(env.get(ENV_ISOLATION_SUBNET4));
		if (subnet != null) {
			runtime.setIpv4Subnet(subnet);
		}

		subnet = StringUtils.trimToNull(env.get(ENV_ISOLATION_SUBNET6));
		if (subnet != null) {
			runtime.setIpv6Subnet(subnet);
		}
	}
}
