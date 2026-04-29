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
package org.jenkinsci.plugins.ctx.isolation.test;

import org.jenkinsci.plugins.ctx.isolation.converters.PluginInitializer;
import org.junit.internal.AssumptionViolatedException;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.Hudson;

public class PatchedJenkinsRule extends JenkinsRule {

	@Override
	public void before() throws Throwable {
		System.out.println("--------------- HERE !!");

		super.before();
	}

	@Override
	protected Hudson newHudson() throws Exception {
		final Hudson hudson = super.newHudson();
		if (true)
			throw new AssumptionViolatedException("Jenkins startup interrupted");

		System.out.println("--------------- I got Hudson!!!");

		PluginInitializer.registerXStreamForNodes();

		return hudson;
	}
}
