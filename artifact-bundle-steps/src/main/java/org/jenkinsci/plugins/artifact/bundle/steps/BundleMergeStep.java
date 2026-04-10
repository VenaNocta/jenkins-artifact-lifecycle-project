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
package org.jenkinsci.plugins.artifact.bundle.steps;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.artifact.bundle.api.context.Bundle;
import org.jenkinsci.plugins.artifact.bundle.api.context.BundleContext;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.TaskListener;

public class BundleMergeStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	private String source;
	private String target;

	@DataBoundConstructor
	public BundleMergeStep() {
	}

	public String getSource() {
		return this.source;
	}

	public String getTarget() {
		return this.target;
	}

	@DataBoundSetter
	public void setSource(final String source) {
		this.source = source;
	}

	@DataBoundSetter
	public void setTarget(final String target) {
		this.target = target;
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new BundleMergeExecution(context, this);
	}

	public static class BundleMergeExecution extends StepExecution {

		private static final long serialVersionUID = 1L;

		private final BundleMergeStep step;

		public BundleMergeExecution(final StepContext context, final BundleMergeStep step) {
			super(context);
			this.step = step;
		}

		protected Bundle getBundle(final BundleContext context, String id) {
			if ((id = StringUtils.trimToNull(id)) == null)
				return context.getBundle();
			return context.getBundle(id);
		}

		protected void log(final Level level, final String msg, final Throwable t) {
			try {
				final PrintStream stream = getContext().get(TaskListener.class)
						.getLogger();
				final int lvl = level == null ? Level.INFO.intValue() : level.intValue();

				final String header;
				if (lvl <= Level.FINE.intValue())
					header = "DEBUG: ";
				else if (lvl <= Level.CONFIG.intValue())
					header = "CONF: ";
				else if (lvl <= Level.INFO.intValue())
					header = "";
				else if (lvl <= Level.WARNING.intValue())
					header = "WARN: ";
				else
					header = "ERROR: ";

				stream.println(header + msg);
				if (t != null)
					t.printStackTrace(stream);
				return;
			} catch (IOException | InterruptedException e) {
			}
			// ok, when it doesn't work we push it to the instance logger
			final LogRecord record = new LogRecord(level, msg);
			if (t != null)
				record.setThrown(t);
			Logger.getLogger(getClass().getName())
					.log(record);
		}

		@Override
		public boolean start() throws Exception {
			final BundleContext context = getContext().get(BundleContext.class);

			final Bundle source = getBundle(context, this.step.getSource());
			final Bundle target = getBundle(context, this.step.getTarget());

			if (source == target) {
				log(Level.WARNING, "Merging skipped: Source == Target", null);
			} else {
				log(Level.INFO, String.format("Merging Bundle (%s) into Bundle (%s)", source.getId(), target.getId()),
						null);
				target.merge(source);
			}

			getContext().onSuccess(null);
			return true;
		}

	}

	@Extension
	public static class BundleMergeDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "bundleMerge";
		}

		@Override
		public String getDisplayName() {
			return Messages.BundleMerge_DisplayName();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return Set.of(BundleContext.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return Set.of();
		}

	}

}
