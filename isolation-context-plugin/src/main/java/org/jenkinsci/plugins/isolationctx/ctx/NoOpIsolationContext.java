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
package org.jenkinsci.plugins.isolationctx.ctx;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;

import org.apache.commons.lang3.NotImplementedException;
import org.jenkinsci.plugins.isolationctx.IsolationContext;
import org.jenkinsci.plugins.isolationctx.ctx.net.DefaultNetLink;

import hudson.FilePath.FileCallable;
import hudson.FilePath;
import hudson.LauncherDecorator;
import hudson.remoting.Future;

public class NoOpIsolationContext implements IsolationContext {

	private static final long serialVersionUID = 1L;

	@Override
	public LauncherDecorator launcherDecorator() {
		// NOOP
		return null;
	}

	@Override
	public void setup() {
	}

	@Override
	public void destroy() {
	}

	@Override
	public FilePath getWorkspace() {
		return null;
	}

	@Override
	public <T> T actInContext(final FileCallable<T> callable) throws IOException, InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> Future<T> actInContextAsync(final FileCallable<T> callable) throws IOException, InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public UnixDomainSocketAddress connectUnixSocket(final String path) {
		// TODO provide localhost Socket
		throw new NotImplementedException();
	}

	@Override
	public NetLink connectNetLink() {
		return DefaultNetLink.createLoopback(true, true);
	}

	@Override
	public void disconnectNetLink(final NetLink link) {
	}
}
