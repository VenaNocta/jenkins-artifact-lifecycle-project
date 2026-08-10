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

import java.util.Set;

public class GitTagPushStep2 extends Step {

	private final SCM scm;
	private final String tag;
	private final String comment;

	private String remoteName = "origin";
	private boolean force = true;

	@DataBoundConstructor
	public GitTagPushStep2(final SCM scm, final String tag, final String comment) {
		this.scm = scm;
		this.tag = Util.fixEmptyAndTrim(tag);
		this.comment = Util.fixEmptyAndTrim(comment);
	}

	public SCM getScm() {
		return this.scm;
	}

	public String getTag() {
		return this.tag;
	}

	public String getComment() {
		return this.comment;
	}

	public String getRemoteName() {
		return this.remoteName;
	}

	public boolean getForcePush() {
		return this.force;
	}

	@DataBoundSetter
	public void setRemoteName(final String remoteName) {
		this.remoteName = remoteName;
	}

	@DataBoundSetter
	public void setForce(final boolean force) {
		this.force = force;
	}

	@Override
	public Execution start(final StepContext context) {
		return new Execution(context, this);
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

		private static final long serialVersionUID = 1L;

		private final transient GitTagPushStep2 step;

		protected Execution(final StepContext context, final GitTagPushStep2 step) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			final StepContext ctx = getContext();
			final Run<?, ?> run = ctx.get(Run.class);
			final FilePath workspace = ctx.get(FilePath.class);
			final TaskListener listener = ctx.get(TaskListener.class);

			if (!(step.getScm() instanceof GitSCM)) {
				throw new AbortException(getClass().getSimpleName() + " only supports GitSCM");
			}

			final GitSCM gitScm = (GitSCM) step.getScm();
			final GitClient git = gitScm.createClient(listener, run.getEnvironment(listener), run, workspace, null);

			// remote config
			RemoteConfig remote = gitScm.getRepositoryByName(this.step.getRemoteName());
			if (remote == null)
				throw new AbortException("Unknown remote: " + this.step.getRemoteName());
			remote = gitScm.getParamExpandedRepo(run.getEnvironment(listener), remote);

			final URIish uri = remote.getURIs()
					.get(0);

			// create/update tag
			final String tag = this.step.getTag();
			git.tag(tag, this.step.getComment());

			// push tag
			git.push()
					.to(uri)
					.ref("refs/tags/" + tag)
					.force(this.step.getForcePush())
					.execute();

			listener.getLogger()
					.println("Successfully pushed tag: " + tag);
			return null;
		}
	}

	@OptionalExtension(requirePlugins = "git")
	public static class Descriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "gitTagPush2";
		}

		@Override
		public String getDisplayName() {
			return "Push Git Tag";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return Set.of(Run.class, FilePath.class, TaskListener.class);
		}
	}
}
