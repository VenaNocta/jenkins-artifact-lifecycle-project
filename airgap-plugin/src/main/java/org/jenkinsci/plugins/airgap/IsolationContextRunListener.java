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

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class IsolationContextRunListener extends RunListener<Run<?, ?>> {

	@Override
	public void onStarted(Run<?, ?> run, TaskListener listener) {
		if (!isAirgapEnabled(run)) {
			return;
		}

		// TODO switch to expandable selector
		IsolationContext context = new LinuxNamespaceContext(run);

		run.addAction(new IsolationContextAction(context));
	}

	@Override
	public void onCompleted(Run<?, ?> run, TaskListener listener) {
		// TODO find a way to teardown all IsolationContext instances
	}

	private boolean isAirgapEnabled(Run<?, ?> run) {
		Job<?, ?> job = run.getParent();
		AirgapJobProperty prop = job.getProperty(AirgapJobProperty.class);
		return prop != null && prop.isEnabled();
	}
}
