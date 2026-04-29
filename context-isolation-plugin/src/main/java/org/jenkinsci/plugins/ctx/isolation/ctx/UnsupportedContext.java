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
package org.jenkinsci.plugins.ctx.isolation.ctx;

import java.io.IOException;
import java.io.Serializable;
import java.net.UnixDomainSocketAddress;

import org.jenkinsci.plugins.ctx.isolation.IsolationContext;

import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.remoting.Future;

public class UnsupportedContext implements IsolationContext {

	private static final long serialVersionUID = 1L;

	public static final String ERR_FORCED_ISOLATION = "Forced Sandbox-Isolation active! - Current Sandbox not isolated!";

	protected DecoratorFactory factory;

	public UnsupportedContext() {
		this.factory = new DecoratorFactory();
	}

	@Override
	public LauncherDecorator launcherDecorator() {
		return this.factory;
	}

	@Override
	public void setup() {
	}

	@Override
	public void destroy() {
	}

	protected static class DecoratorFactory extends LauncherDecorator implements Serializable {

		private static final long serialVersionUID = 1L;

		@Override
		public Launcher decorate(final Launcher launcher, final Node node) {
			return new Launcher.DecoratedLauncher(launcher) {
				@Override
				public Proc launch(final ProcStarter starter) throws IOException {
					throw new AbortException(ERR_FORCED_ISOLATION);
				}
			};
		}
	}

	@Override
	public <T> T actInContext(final FileCallable<T> callable) throws IOException, InterruptedException {
		throw new AbortException(ERR_FORCED_ISOLATION);
	}

	@Override
	public <T> Future<T> actInContextAsync(final FileCallable<T> callable) throws IOException, InterruptedException {
		throw new AbortException(ERR_FORCED_ISOLATION);
	}

	@Override
	public UnixDomainSocketAddress connectUnixSocket(final String path) {
		throw new UnsupportedOperationException(ERR_FORCED_ISOLATION);
	}

	@Override
	public NetLink connectNetLink() {
		throw new UnsupportedOperationException(ERR_FORCED_ISOLATION);
	}

	@Override
	public void disconnectNetLink(final NetLink link) {
	}
}
