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
package org.jenkinsci.plugins.isolationctx.hooks;

import java.io.IOException;
import org.jenkinsci.plugins.isolationctx.IsolationContext;
import org.jenkinsci.plugins.isolationctx.dto.IsolationContextAction;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.Extension;

@Extension
public class IsolationContextGraphListener implements GraphListener.Synchronous {

	@Override
	public void onNewHead(final FlowNode node) {
		try {
			onNewStepEndNode(node);
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
		}
	}

	protected void onNewStepEndNode(final FlowNode flowNode) throws InterruptedException, IOException {
		if (!(flowNode instanceof BlockEndNode<?>))
			return;

		final BlockEndNode<?> end = (BlockEndNode<?>) flowNode;
		final IsolationContextAction action = end.getStartNode()
				.getAction(IsolationContextAction.class);

		if (action == null)
			return;

		final IsolationContext isolation = action.getContextOrNull();
		try {
			if (isolation != null)
				isolation.destroy();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
