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
package org.jenkinsci.plugins.ctx.proxy.repo.steps;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jenkinsci.plugins.ctx.isolation.IsolationContext;
import org.jenkinsci.plugins.ctx.isolation.IsolationContext.NetLink;
import org.jenkinsci.plugins.ctx.isolation.WorkflowUtils;
import org.jenkinsci.plugins.ctx.isolation.config.IsolationContextAction;
import org.jenkinsci.plugins.ctx.proxy.repo.ProxyInstance;
import org.jenkinsci.plugins.ctx.proxy.repo.ProxyProvider;
import org.jenkinsci.plugins.ctx.proxy.repo.config.ProxyConfig;
import org.jenkinsci.plugins.ctx.proxy.repo.config.ProxyConfigDescriptor;
import org.jenkinsci.plugins.ctx.proxy.repo.config.RepoProxyConfigurationProvider;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.CauseOfInterruption;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public class RepoProxyStepExecution extends StepExecution {

	private static final long serialVersionUID = 1L;

	protected final transient CompletableFuture<CauseOfInterruption> runFuture = new CompletableFuture<>();
	protected final transient Map<String, ProxyInstance> activeProxies = new LinkedHashMap<>();

	protected final RepoProxyStep step;

	protected RepoProxyStepExecution(@NonNull final StepContext context, @NonNull final RepoProxyStep step) {
		super(context);
		this.step = step;
	}

	@Override
	public boolean start() throws Exception {
		final StepContext context = getContext();
		final TaskListener listener = context.get(TaskListener.class);
		final PrintStream log = listener.getLogger();

		final IsolationContextAction action = WorkflowUtils.findContextAction( //
				context.get(Run.class), context.get(FlowNode.class));
		final IsolationContext isoContext = action == null ? null : action.getContext();
		if (isoContext == null)
			throw new RuntimeException("IsolationContext unavailable!");

		final NetLink link = isoContext.connectNetLink();

		if (link == null)
			throw new RuntimeException("Failed to connect the Proxy to the IsolationContext!");

		this.runFuture.handle((v, e) -> {
			if (v != null) {
				context.onFailure(new FlowInterruptedException(Result.FAILURE, true, v));
			} else if (e != null) {
				context.onFailure(e);
			}
			isoContext.disconnectNetLink(link);
			return null;
		});

		final Map<String, String> proxyEnv = new LinkedHashMap<>();
		final List<IpAddress<?>> proxyIps = Arrays.asList(link.getHostAddressV4(), link.getHostAddressV6())
				.stream()
				.filter(Objects::nonNull)
				.toList();
		if (proxyIps.isEmpty()) {
			log.println("[ RepoProxy ] NOTICE: No Proxy IPs are available! Proxy Startup skipped!");
		} else {
			final Map<String, ProxyConfig> configs = new LinkedHashMap<>();
			for (Entry<String, String> entry : this.step.getRequestedRepos()
					.entrySet()) {
				final String id = entry.getKey();
				final ProxyConfig config = RepoProxyConfigurationProvider.findConfig(context.get(Run.class), id);
				if (config == null)
					log.println("[ RepoProxy ] Proxy unavailable! ID: " + id);
				else
					configs.put(entry.getValue(), config);
			}

			spawnProxies(proxyEnv, configs, proxyIps);
		}

		context.newBodyInvoker()
				.withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class),
						EnvironmentExpander.constant(proxyEnv)))
				.withCallback(new BlockCallback(this))
				.start();
		return false;
	}

	protected void spawnProxies(final Map<String, String> proxyEnv, final Map<String, ProxyConfig> configs,
			final List<IpAddress<?>> ips) throws IOException, InterruptedException {
		final StepContext context = getContext();
		final FlowNode flowNode = context.get(FlowNode.class);
		final TaskListener listener = context.get(TaskListener.class);
		final PrintStream log = listener.getLogger();

		for (Entry<String, ProxyConfig> entry : configs.entrySet()) {
			final ProxyConfig config = entry.getValue();
			final ProxyProvider provider = findProvider(config.getDescriptor());
			if (provider == null) {
				log.println("[ RepoProxy ] Proxy unsupported! ID: " + config.getId());
				continue;
			}

			final ProxyInstance instance = provider.createInstance(flowNode, entry.getKey(), config, this.runFuture);
			if (instance == null) {
				log.println("[ RepoProxy ] Proxy Configuration failed! ID: " + config.getId());
				continue;
			}

			this.activeProxies.put(config.getId(), instance);
			for (IpAddress<?> ip : ips) {
				instance.bind(ip, proxyEnv, listener);
			}
		}
	}

	protected ProxyProvider findProvider(final ProxyConfigDescriptor configDescriptor) {
		for (ProxyProvider provider : ProxyProvider.all()) {
			if (provider.isApplicable(configDescriptor))
				return provider;
		}
		return null;
	}

	public void shutdown() {
		if (this.runFuture != null) {
			this.runFuture.complete(null);
			final List<CompletableFuture<?>> lst = new LinkedList<>();
			for (ProxyInstance instance : this.activeProxies.values()) {
				final CompletableFuture<?> future = instance.onStop();
				if (future != null)
					lst.add(future);
			}
			try {
				CompletableFuture.allOf(lst.toArray(new CompletableFuture<?>[lst.size()]))
						.get(60, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException ignored) {
			}
		}
	}

	private static class BlockCallback extends BodyExecutionCallback {

		private static final long serialVersionUID = 1L;

		protected final RepoProxyStepExecution exec;

		public BlockCallback(final RepoProxyStepExecution exec) {
			this.exec = exec;
		}

		@Override
		public void onSuccess(final StepContext context, final Object result) {
			this.exec.shutdown();
			this.exec.getContext()
					.onSuccess(result);
		}

		@Override
		public void onFailure(final StepContext context, final Throwable t) {
			this.exec.shutdown();
			this.exec.getContext()
					.onFailure(t);
		}
	}
}