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

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.model.Node;

public class NoOpIsolationContext implements IsolationContext {

	@Override
	public LauncherDecorator launcherDecorator() {
		return new LauncherDecorator() {

			@Override
			public Launcher decorate(Launcher launcher, Node node) {
				// NOOP
				return launcher;
			}
		};
	}
}
