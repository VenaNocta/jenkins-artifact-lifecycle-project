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
package org.jenkinsci.plugins.isolationctx.provider;

import java.io.IOException;

import org.jenkinsci.plugins.isolationctx.handler.LnxNodeIsolationHandler;
import org.jenkinsci.plugins.isolationctx.handler.NodeIsolationHandler;
import org.jenkinsci.plugins.isolationctx.handler.NodeIsolationHandlerProvider;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;

@Extension
public class LnxNodeIsolationHandlerProvider implements NodeIsolationHandlerProvider {

	@Override
	public boolean isApplicable(final Node node) {
		if (node == null)
			return false;
		final Computer computer = node.toComputer();
		if (computer == null)
			return false;

		try {
			final Object value = computer.getSystemProperties()
					.get("os.name");
			if (value instanceof String os) {
				return os.toLowerCase()
						.contains("linux");
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public NodeIsolationHandler buildHandler(final Node node) {
		return new LnxNodeIsolationHandler(node);
	}
}
