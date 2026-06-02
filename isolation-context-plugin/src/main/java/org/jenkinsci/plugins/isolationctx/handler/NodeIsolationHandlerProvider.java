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
package org.jenkinsci.plugins.isolationctx.handler;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Node;

public interface NodeIsolationHandlerProvider extends ExtensionPoint {

	public boolean isApplicable(Node node);

	public NodeIsolationHandler buildHandler(Node node);

	public static ExtensionList<NodeIsolationHandlerProvider> all() {
		return ExtensionList.lookup(NodeIsolationHandlerProvider.class);
	}

	public static NodeIsolationHandlerProvider providerFor(final Node node) {
		return all().stream()
				.filter(p -> p.isApplicable(node))
				.findFirst()
				.orElse(null);
	}
}
