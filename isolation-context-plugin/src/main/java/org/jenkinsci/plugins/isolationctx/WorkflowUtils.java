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
package org.jenkinsci.plugins.isolationctx;

import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.model.Action;
import hudson.model.Run;

// class requires org.jenkinsci.plugins.workflow.graph package
public class WorkflowUtils {

	private WorkflowUtils() {
	}

	public static <T extends Action> T findContextAction(final Class<T> type, final FlowNode flowNode) {
		T action = flowNode.getAction(type);
		if (action != null)
			return action;

		for (BlockStartNode node : flowNode.iterateEnclosingBlocks()) {
			action = node.getAction(type);
			if (action != null)
				return action;
		}
		return null;
	}

	public static <T extends Action> T findContextAction(final Class<T> type, final Run<?, ?> run,
			final FlowNode flowNode) {
		T action = findContextAction(type, flowNode);
		if (action != null)
			return action;
		action = run.getAction(type);
		if (action != null)
			return action;
		return null;
	}
}
