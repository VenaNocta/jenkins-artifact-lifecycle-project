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
package org.jenkinsci.plugins.repoproxy.config;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;

public class ScmBomSource extends BomSource {

	private static final long serialVersionUID = 1L;

	protected final String version;
	protected final SCMSource scm;

	public ScmBomSource(final UUID uuid, final String name, final String version, final SCMSource scm) {
		super(uuid, name);
		this.version = Util.fixEmptyAndTrim(version);
		this.scm = scm;
	}

	@DataBoundConstructor
	public ScmBomSource(final String uuid, final String name, final String version, final SCMSource scm) {
		super(UUID.fromString(uuid), name);
		this.version = Util.fixEmptyAndTrim(version);
		this.scm = scm;
	}

	public String getVersion() {
		return this.version;
	}

	public SCMSource getScm() {
		return this.scm;
	}

	@Override
	public BomSourceCache buildCache(final Run<?, ?> run, final Launcher nodeBasedLauncher, final FilePath cachePath,
			final TaskListener listener) throws IOException, InterruptedException {
		cachePath.mkdirs();
		retrieve(run, nodeBasedLauncher, cachePath, new StreamTaskListener(cachePath.withSuffix(".log")
				.write(), StandardCharsets.UTF_8));
		return new ScmBomSourceCache(this.uuid, this.version, cachePath);
	}

	public void retrieve(final Run<?, ?> run, final Launcher nodeBasedLauncher, final FilePath target,
			final TaskListener listener) throws IOException, InterruptedException {
		final SCMRevision revision = retrySCMOperation(listener,
				() -> this.scm.fetch(this.version, listener, run.getParent()));
		if (revision == null) {
			throw new AbortException("No version " + this.version + " found for Bom-Repo " + this.name);
		}
		final SCM scm = this.scm.build(revision.getHead(), revision);
		retrySCMOperation(listener, () -> {
			scm.checkout(run, nodeBasedLauncher, target, listener, null, null);
			return null;
		});
	}

	protected static <T> T retrySCMOperation(final TaskListener listener, final Callable<T> task)
			throws IOException, InterruptedException {
		for (int retryCount = Jenkins.get()
				.getScmCheckoutRetryCount(); retryCount >= 0; retryCount--) {
			try {
				return task.call();
			} catch (AbortException e) {
				// abort exception might have a null message.
				// If so, just skip echoing it.
				if (e.getMessage() != null) {
					listener.error(e.getMessage());
				}
			} catch (InterruptedIOException e) {
				throw e;
			} catch (Exception e) {
				// checkout error not yet reported
				Functions.printStackTrace(e, listener.error("Checkout failed"));
			}

			if (retryCount == 0) // all attempts failed
				throw new AbortException("Maximum checkout retry attempts reached, aborting");

			listener.getLogger()
					.println("Retrying after 10 seconds");
			Thread.sleep(10000);
		}
		return null;
	}

	@Extension
	@Symbol("scmSource")
	public static class Descriptor extends BomSourceDescriptor {

		@Override
		public String getDisplayName() {
			return "SCM Bom Repository";
		}

		/**
		 * Returns only implementations overriding
		 * {@link SCMSource#retrieve(String, TaskListener)} or
		 * {@link SCMSource#retrieve(String, TaskListener, Item)}.
		 */
		@Restricted(NoExternalUse.class) // Jelly, Hider
		public Collection<SCMSourceDescriptor> getSCMDescriptors() {
			final List<SCMSourceDescriptor> descriptors = new ArrayList<>();
			for (SCMSourceDescriptor d : ExtensionList.lookup(SCMSourceDescriptor.class)) {
				if (Util.isOverridden(SCMSource.class, d.clazz, "retrieve", String.class, TaskListener.class)
						|| Util.isOverridden(SCMSource.class, d.clazz, "retrieve", String.class, TaskListener.class,
								Item.class)) {
					descriptors.add(d);
				}
			}
			return descriptors;
		}
	}
}
