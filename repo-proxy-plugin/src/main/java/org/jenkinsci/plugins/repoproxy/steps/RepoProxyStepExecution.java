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
package org.jenkinsci.plugins.repoproxy.steps;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.isolationctx.IsolationContext;
import org.jenkinsci.plugins.isolationctx.WorkflowUtils;
import org.jenkinsci.plugins.isolationctx.IsolationContext.NetLink;
import org.jenkinsci.plugins.isolationctx.config.IsolationContextAction;
import org.jenkinsci.plugins.repoproxy.ProxyBootstrapper;
import org.jenkinsci.plugins.repoproxy.ProxyProvider;
import org.jenkinsci.plugins.repoproxy.config.ProxyConfig;
import org.jenkinsci.plugins.repoproxy.config.ProxyConfigDescriptor;
import org.jenkinsci.plugins.repoproxy.config.RepoProxyConfigurationProvider;
import org.jenkinsci.plugins.repoproxy.config.SBomSourceCache;
import org.jenkinsci.plugins.repoproxy.config.SBomSourceProvider;
import org.jenkinsci.plugins.repoproxy.netty.AgentNettyStopCallable;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.agents.ControllerToAgentFileCallable;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class RepoProxyStepExecution extends StepExecution {

	private static final long serialVersionUID = 1L;

	protected final transient Map<String, ProxyBootstrapper> activeProxies = new LinkedHashMap<>();
	protected final transient Map<UUID, UUID> activeNettyHandleCacheRefMap = new LinkedHashMap<>();

	protected final RepoProxyStep step;
	protected final FilePath cacheFolder;
	protected final FilePath cacheSBomFolder;

	protected transient NetLink link;

	protected transient WorkspaceAction _ws = null;

	protected BodyExecution bodyExec = null;

	protected RepoProxyStepExecution(@NonNull final StepContext context, @NonNull final RepoProxyStep step)
			throws IOException, InterruptedException {
		super(context);
		this.step = step;
		this.cacheFolder = getWorkspace().withSuffix("@proxy_cache");
		this.cacheSBomFolder = getWorkspace().withSuffix("@proxy_sbom_cache");
	}

	protected synchronized WorkspaceAction getWorkspaceAction() throws IOException, InterruptedException {
		if (this._ws != null)
			return this._ws;
		final StepContext context = getContext();
		return this._ws = WorkflowUtils.findContextAction( //
				WorkspaceAction.class, context.get(Run.class), context.get(FlowNode.class));
	}

	protected FilePath getWorkspace() throws IOException, InterruptedException {
		return getWorkspaceAction().getWorkspace();
	}

	protected IsolationContext getIsolationContext() throws IOException, InterruptedException {
		final StepContext context = getContext();
		final IsolationContextAction action = WorkflowUtils.findContextAction( //
				IsolationContextAction.class, context.get(Run.class), context.get(FlowNode.class));
		return action == null ? null : action.getContext();
	}

	protected IsolationContext tryGetIsolationContext() {
		try {
			return getIsolationContext();
		} catch (IOException | InterruptedException ignored) {
			ignored.printStackTrace(System.err);
		}
		return null;
	}

	protected FilePath getIsolatedWorkspace() throws IOException, InterruptedException {
		final IsolationContext isolationCtx = getIsolationContext();
		if (isolationCtx == null)
			return null;
		return isolationCtx.getWorkspace();
	}

	@Override
	public boolean start() throws Exception {
		final StepContext context = getContext();
		final Run<?, ?> run = context.get(Run.class);
		final TaskListener listener = context.get(TaskListener.class);
		final PrintStream log = listener.getLogger();

		final IsolationContext isoContext = getIsolationContext();
		if (isoContext == null)
			throw new RuntimeException("IsolationContext unavailable!");

		final NetLink link = this.link = isoContext.connectNetLink();
		if (link == null)
			throw new RuntimeException("Failed to connect the Proxy to the IsolationContext!");

		final Map<String, String> proxyEnv = new LinkedHashMap<>();
		final Set<IpAddress<?>> proxyIps = Arrays.asList(link.getHostAddressV4(), link.getHostAddressV6())
				.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (proxyIps.isEmpty()) {
			log.println("[ RepoProxy ] NOTICE: No Proxy IPs are available! Proxy Startup skipped!");
		} else {
			final Map<String, ProxyConfig> configs = new LinkedHashMap<>();
			for (Entry<String, String> entry : this.step.getRequestedRepos()
					.entrySet()) {
				final String configKey = entry.getKey();
				final String refKey = entry.getValue();

				if (configs.containsKey(refKey)) {
					log.println("[ RepoProxy ] Proxy skipped, RefKey already exists! Name: " + configKey + " | RefKey: "
							+ refKey);
					continue;
				}

				final ProxyConfig config = RepoProxyConfigurationProvider.findConfigByName(run, configKey);
				if (config == null)
					log.println("[ RepoProxy ] Proxy unavailable! Name: " + configKey);
				else
					configs.put(refKey, config);
			}

			spawnProxies(link, proxyEnv, configs, proxyIps);
		}

		this.bodyExec = context.newBodyInvoker()
				.withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class),
						EnvironmentExpander.constant(proxyEnv)))
				.withCallback(new BlockCallback(this))
				.start();
		return false;
	}

	protected void spawnProxies(final NetLink link, final Map<String, String> proxyEnv,
			final Map<String, ProxyConfig> configs, final Set<IpAddress<?>> ips)
			throws IOException, InterruptedException {
		final StepContext context = getContext();
		final Run<?, ?> run = context.get(Run.class);
		final FlowNode flowNode = context.get(FlowNode.class);
		final TaskListener listener = context.get(TaskListener.class);
		final PrintStream log = listener.getLogger();

		final Set<UUID> sbomIds = new LinkedHashSet<>();
		final Set<String> credIds = new LinkedHashSet<>();

		for (Entry<String, ProxyConfig> entry : configs.entrySet()) {
			final String refKey = entry.getKey();
			final ProxyConfig config = entry.getValue();
			final ProxyProvider provider = findProvider(config.getDescriptor());
			if (provider == null) {
				log.println("[ RepoProxy ] Proxy unsupported! ID: " + config.getId());
				continue;
			}

			final ProxyBootstrapper bootstrapper = provider.createBootstrapper(flowNode, refKey, config, ips);
			if (bootstrapper == null) {
				log.println("[ RepoProxy ] Proxy Configuration failed! ID: " + config.getId());
				continue;
			}

			this.activeProxies.put(refKey, bootstrapper);
			sbomIds.addAll(bootstrapper.getRequiredSBoms());
			credIds.addAll(bootstrapper.getRequiredCredentials());
		}

		// preload data
		final Map<UUID, SBomSourceCache> sbomCacheMap = new LinkedHashMap<>();
		final Map<String, IdCredentials> credMap = new LinkedHashMap<>();
		{
			final Launcher nodeBasedLauncher = this.cacheSBomFolder.createLauncher(listener);
			for (UUID uuid : sbomIds) {
				sbomCacheMap.put(uuid, SBomSourceProvider.findSourceById(run, uuid)
						.buildCache(run, nodeBasedLauncher, this.cacheSBomFolder.child(uuid.toString()), listener));
			}
			for (String credId : credIds) {
				final IdCredentials cred = CredentialsProvider.findCredentialById(credId, IdCredentials.class, run);
				if (cred == null)
					continue;
				CredentialsProvider.track(run, cred);
				credMap.put(credId, cred);
			}
		}

		for (ProxyBootstrapper bootstrap : new HashSet<>(this.activeProxies.values())) {
			// find novel cacheRef
			UUID cacheRef;
			do {
				cacheRef = UUID.randomUUID();
			} while (this.activeNettyHandleCacheRefMap.containsValue(cacheRef));
			// create new cachePath for bootstrap in context and call it
			final ControllerToAgentFileCallable<UUID> callable = //
					bootstrap.serverStartCallable(link, context, proxyEnv, sbomCacheMap, credMap, listener);
			final FilePath dir = this.cacheFolder.child(cacheRef.toString());
			dir.mkdirs();
			this.activeNettyHandleCacheRefMap.put(dir.act(callable), cacheRef);
		}
	}

	protected ProxyProvider findProvider(final ProxyConfigDescriptor configDescriptor) {
		for (ProxyProvider provider : ProxyProvider.all()) {
			if (provider.isApplicable(configDescriptor))
				return provider;
		}
		return null;
	}

	@Override
	public void stop(final Throwable cause) throws Exception {
		shutdown();
		if (this.bodyExec != null)
			this.bodyExec.cancel(cause);
		super.stop(cause);
	}

	public void shutdown() throws IOException, InterruptedException {
		final IsolationContext isoContext = getIsolationContext();
		if (isoContext != null) {
			isoContext.actInContext(new AgentNettyStopCallable(this.activeNettyHandleCacheRefMap.keySet()));
		}
		this.cacheFolder.deleteRecursive();
		this.cacheSBomFolder.deleteRecursive();
	}

	private static class BlockCallback extends BodyExecutionCallback {

		private static final long serialVersionUID = 1L;

		protected final RepoProxyStepExecution exec;

		public BlockCallback(final RepoProxyStepExecution exec) {
			this.exec = exec;
		}

		@Override
		public void onSuccess(final StepContext context, final Object result) {
			try {
				this.exec.shutdown();
			} catch (IOException | InterruptedException ignored) {
			}
			this.exec.getContext()
					.onSuccess(result);
		}

		@Override
		public void onFailure(final StepContext context, final Throwable t) {
			try {
				this.exec.shutdown();
			} catch (IOException | InterruptedException ignored) {
			}
			this.exec.getContext()
					.onFailure(t);
		}
	}
}