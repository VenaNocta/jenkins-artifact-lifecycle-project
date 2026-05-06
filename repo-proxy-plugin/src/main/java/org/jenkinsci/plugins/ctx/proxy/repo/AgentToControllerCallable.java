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
package org.jenkinsci.plugins.ctx.proxy.repo;

import org.jenkinsci.remoting.RoleChecker;

import hudson.remoting.Callable;
import jenkins.security.Roles;

// TODO replace with official once it exists upstream!
public interface AgentToControllerCallable<V, T extends Throwable> extends Callable<V, T> {

	@Override
	public default void checkRoles(final RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.MASTER);
	}
}
