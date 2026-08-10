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
package org.jenkinsci.plugins.isolationctx.config;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.Util;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;

public class NodeIsolationProperty extends NodeProperty<Node> {

	private final boolean disabled;
	private final String adminGroup;

	public NodeIsolationProperty() {
		this(false, null);
	}

	@DataBoundConstructor
	public NodeIsolationProperty(final boolean disabled, final String adminGroup) {
		this.disabled = disabled;
		this.adminGroup = Util.fixEmptyAndTrim(adminGroup);
	}

	public boolean isDisabled() {
		return this.disabled;
	}

	public String getAdminGroup() {
		return this.adminGroup;
	}

	public static boolean isDisabled(final Node node) {
		// isolation is disabled, when the Node is null!
		if (node == null)
			return true;
		final NodeIsolationProperty prop = node.getNodeProperty(NodeIsolationProperty.class);
		if (prop == null)
			return false;
		return prop.isDisabled();
	}

	public static String getAdminGroup(final Node node) {
		final NodeIsolationProperty prop = node.getNodeProperty(NodeIsolationProperty.class);
		if (prop == null)
			return null;
		return prop.getAdminGroup();
	}

	@Extension
	public static class DescriptorImpl extends NodePropertyDescriptor {
		@Override
		public String getDisplayName() {
			return "Context Isolation Defaults";
		}
	}
}
