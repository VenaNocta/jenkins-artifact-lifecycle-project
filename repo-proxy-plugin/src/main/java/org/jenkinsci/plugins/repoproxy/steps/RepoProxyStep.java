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
package org.jenkinsci.plugins.repoproxy.steps;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

public class RepoProxyStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	protected final Map<String, String> requestedRepos;

	@DataBoundConstructor
	public RepoProxyStep(final Map<String, String> reqRepos) {
		if (reqRepos == null)
			this.requestedRepos = Collections.emptyMap();
		else
			this.requestedRepos = reqRepos.entrySet()
					.stream()
					.filter(RepoProxyStep::entryFilter)
					.collect(Collectors.toMap(Map.Entry::getKey, RepoProxyStep::getSanitizedValue));
	}

	public Map<String, String> getRequestedRepos() {
		return this.requestedRepos;
	}

	protected static boolean entryFilter(final Map.Entry<String, String> entry) {
		if (entry == null)
			return false;
		if (StringUtils.isBlank(entry.getKey()) || StringUtils.isBlank(entry.getValue()))
			return false;
		return true;
	}

	// sanitize value to be used in ENV variables
	protected static String getSanitizedValue(final Map.Entry<String, String> entry) {
		final String value = Util.fixEmptyAndTrim(entry.getValue());
		if (value == null)
			return null;
		return value.replaceAll("[^A-Za-z0-9]+", "_")
				.replaceAll("^_|_$", "");
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new RepoProxyStepExecution(context, this);
	}

	@Extension
	public static class Descriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "repoProxy";
		}

		@Override
		public String getDisplayName() {
			return Messages.RepoProxy_DisplayName();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return ImmutableSet.of(Node.class, Run.class, FlowNode.class, TaskListener.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return ImmutableSet.of();
		}

		@Override
		public boolean takesImplicitBlockArgument() {
			return true;
		}

		@Override
		public boolean isAdvanced() {
			return true;
		}
	}
}
