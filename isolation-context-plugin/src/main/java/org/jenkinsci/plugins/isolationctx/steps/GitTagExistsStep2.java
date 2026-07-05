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
package org.jenkinsci.plugins.isolationctx.steps;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Map;
import java.util.Set;

public class GitTagExistsStep2 extends Step {

	private final SCM scm;
	private final String tag;

	private String remoteName = "origin";

	@DataBoundConstructor
	public GitTagExistsStep2(final SCM scm, final String tag) {
		this.scm = scm;
		this.tag = Util.fixEmptyAndTrim(tag);
	}

	public SCM getScm() {
		return this.scm;
	}

	public String getTag() {
		return this.tag;
	}

	public String getRemoteName() {
		return this.remoteName;
	}

	@DataBoundSetter
	public void setRemoteName(String remoteName) {
		this.remoteName = remoteName;
	}

	@Override
	public Execution start(final StepContext context) {
		return new Execution(context, this);
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Boolean> {

		private static final long serialVersionUID = 1L;

		private final transient GitTagExistsStep2 step;

		protected Execution(final StepContext context, final GitTagExistsStep2 step) {
			super(context);
			this.step = step;
		}

		@Override
		protected Boolean run() throws Exception {
			final StepContext ctx = getContext();
			final Run<?, ?> run = ctx.get(Run.class);
			final FilePath workspace = ctx.get(FilePath.class);
			final TaskListener listener = ctx.get(TaskListener.class);

			if (!(step.getScm() instanceof GitSCM)) {
				throw new AbortException("GitCheckForTagStep2 only supports GitSCM");
			}

			final GitSCM gitScm = (GitSCM) step.getScm();
			final GitClient git = gitScm.createClient(listener, run.getEnvironment(listener), run, workspace, null);

			RemoteConfig remote = gitScm.getRepositoryByName(step.getRemoteName());
			if (remote == null)
				throw new AbortException("Unknown remote: " + step.getRemoteName());

			remote = gitScm.getParamExpandedRepo(run.getEnvironment(listener), remote);

			final URIish uri = remote.getURIs()
					.get(0);

			final String tag = step.getTag();
			if (tag == null)
				throw new AbortException("Tag must not be empty");

			final Map<String, ObjectId> refs = git.getRemoteReferences(uri.toString(), "refs/tags/" + tag, false, true);
			final boolean exists = !refs.isEmpty();

			listener.getLogger()
					.println("[gitTagExists2] remote=" + this.step.getRemoteName() //
							+ " tag=" + tag //
							+ " exists=" + exists);
			return exists;
		}
	}

	@OptionalExtension(requirePlugins = "git")
	public static class Descriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "gitTagExists2";
		}

		@Override
		public String getDisplayName() {
			return "Remote Git Tag exists?";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return Set.of(Run.class, FilePath.class, TaskListener.class);
		}
	}
}
