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
package org.jenkinsci.plugins.airgap;

import org.jenkinsci.plugins.airgap.config.IsolationProperty;
import org.jenkinsci.plugins.airgap.ctx.UnsupportedContext;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class IsolationContextRunListener extends RunListener<Run<?, ?>> {

	@Override
	public void onStarted(Run<?, ?> run, TaskListener listener) {
		if (!isIsolationForced(run) || run.getAction(IsolationContextAction.class) != null)
			return;

		final IsolationContext context = new UnsupportedContext();
		run.addAction(new IsolationContextAction(context));
	}

	@Override
	public void onCompleted(final Run<?, ?> run, final TaskListener listener) {
		final IsolationContextAction action = run.getAction(IsolationContextAction.class);
		if (action != null) {
			action.getContext()
					.destroy();
		}
	}

	public static boolean isIsolationForced(final Run<?, ?> run) {
		return isIsolationForced(run.getParent());
	}

	public static boolean isIsolationForced(final Job<?, ?> job) {
		final IsolationProperty prop = job.getProperty(IsolationProperty.class);
		return prop != null && prop.isEnabled();
	}
}
