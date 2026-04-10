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
package org.jenkinsci.plugins.artifact.bundle.api.steps;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.jenkinsci.plugins.artifact.bundle.api.context.BundleContext;
import org.jenkinsci.plugins.artifact.bundle.api.context.Bundles;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.httpclient.RobustHTTPClient;
import jenkins.util.VirtualFile;

public abstract class BundleStepExecution extends StepExecution {

	private static final long serialVersionUID = 1L;

	protected BundleStepExecution(@NonNull StepContext context) {
		super(context);
	}

	protected Logger getLogger() {
		return Logger.getLogger(getClass().getName());
	}

	protected void log(final Level level, final String msg, final Throwable t) {
		try {
			final PrintStream stream = getTaskListener().getLogger();
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
		getLogger().log(record);
	}

	protected Run<?, ?> getRun() throws IOException, InterruptedException {
		return getContext().get(Run.class);
	}

	protected EnvVars getEnv() throws IOException, InterruptedException {
		return getContext().get(EnvVars.class);
	}

	protected TaskListener getTaskListener() throws IOException, InterruptedException {
		return getContext().get(TaskListener.class);
	}

	protected String getArtifactPaths(String artifacts) throws IOException, InterruptedException {
		if (artifacts != null && getRun() instanceof AbstractBuild) { // no expansion in pipelines
			artifacts = getEnv().expand(artifacts);
		}
		return artifacts;
	}

	protected BundleContext getBundleContext() throws IOException, InterruptedException {
		return getContext().get(BundleContext.class);
	}

	protected Result getResult() throws IOException, InterruptedException {
		final StepContext context = getContext();
		if (context.get(FlowExecution.class) instanceof CpsFlowExecution exec) {
			return exec.getResult();
		}
		return context.get(Run.class)
				.getResult();
	}

	protected void setResult(final Result result) throws IOException, InterruptedException {
		final StepContext context = getContext();
		if (context.get(FlowExecution.class) instanceof CpsFlowExecution exec) {
			exec.setResult(result);
		} else {
			context.get(Run.class)
					.setResult(result);
		}
	}

	protected <S extends Serializable> S getEntry(final Map<String, Serializable> metadata, final Class<S> type,
			final String... keys) {
		return Bundles.getMetadataEntry(metadata, type, keys);
	}

	protected String getEntryString(final Map<String, Serializable> metadata, final String... keys) {
		return StringUtils.trimToNull(getEntry(metadata, String.class, keys));
	}

	protected String fullPath(final VirtualFile root, final VirtualFile file) {
		return fullPath(root, file, null);
	}

	protected String fullPath(final VirtualFile root, final VirtualFile file, final String prefix,
			final String suffix) {
		return fullPath(root, file, name -> String.format("%s%s.%s", prefix, name, suffix));
	}

	protected String fullPath(@NonNull final VirtualFile root, @NonNull final VirtualFile file,
			final Function<String, String> nameMapper) {
		return Bundles.fullPath(root, file, nameMapper);
	}

	protected byte[] download(final VirtualFile source, final String msgFmt) throws IOException, InterruptedException {
		final RobustHTTPClient httpClient = new RobustHTTPClient();
		final URL url = source.toExternalURL();

		try (final ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			if (url == null) {
				try (InputStream dataStream = source.open()) {
					dataStream.transferTo(output);
				}
			} else {
				httpClient.connect("download", String.format(msgFmt, RobustHTTPClient.sanitize(url)),
						client -> client.execute(new HttpGet(url.toString())), response -> {
							try (InputStream is = response.getEntity()
									.getContent()) {
								is.transferTo(output);
							}
						}, getTaskListener());
			}
			return output.toByteArray();
		}
	}

}