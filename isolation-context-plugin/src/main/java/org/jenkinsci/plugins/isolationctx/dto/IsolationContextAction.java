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
package org.jenkinsci.plugins.isolationctx.dto;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.jenkinsci.plugins.isolationctx.IsolationContext;
import org.jenkinsci.plugins.isolationctx.IsolationContextProvider.IsolationContextBuilder;
import org.jenkinsci.plugins.workflow.actions.FlowNodeAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import hudson.model.InvisibleAction;

public class IsolationContextAction extends InvisibleAction implements FlowNodeAction {

	protected final IsolationContextBuilder builder;

	protected IsolationContext context = null;

	protected transient FlowNode _flowNode = null;
	private transient CompletableFuture<IsolationContext> _future = null;

	public IsolationContextAction(final IsolationContext context) {
		this.builder = null;
		this.context = context;
		this._future = CompletableFuture.completedFuture(this.context);
	}

	public IsolationContextAction(final FlowNode flowNode, final IsolationContextBuilder builder) {
		this.context = null;
		this.builder = builder;
		this._flowNode = flowNode;
		this._future = null;
	}

	protected synchronized CompletableFuture<IsolationContext> getContextFuture() {
		if (this._future != null)
			return this._future;
		if (this.builder == null)
			return this._future = CompletableFuture.completedFuture(null);
		return this._future = CompletableFuture.supplyAsync(this::build);
	}

	protected IsolationContext build() {
		try {
			final IsolationContext context = this.builder.build(this._flowNode);
			context.setup();
			return this.context = context;
		} catch (Throwable e) {
			throw new CompletionException(e);
		}
	}

	public synchronized IsolationContext getContext() throws InterruptedException, IOException {
		try {
			return getContextFuture().get();
		} catch (ExecutionException e) {
			final Throwable t = e.getCause();
			if (t instanceof CancellationException ex)
				return null;
			if (t instanceof InterruptedException ex)
				throw ex;
			if (t instanceof RuntimeException ex)
				throw ex;
			if (t instanceof IOException ex)
				throw ex;
			if (t != null)
				throw new IOException(t);
			return null;
		}
	}

	public IsolationContext getContextOrNull() throws InterruptedException, IOException {
		try {
			return getContextFuture().getNow(null);
		} catch (CompletionException e) {
			final Throwable t = e.getCause();
			if (t instanceof CancellationException ex)
				return null;
			if (t instanceof InterruptedException ex)
				throw ex;
			if (t instanceof RuntimeException ex)
				throw ex;
			if (t instanceof IOException ex)
				throw ex;
			if (t != null)
				throw new IOException(t);
			return null;
		}
	}

	@Override
	public void onLoad(final FlowNode parent) {
		this._flowNode = parent;
		if (this.context != null) {
			this.context.onLoad(parent);
			this._future = CompletableFuture.completedFuture(this.context);
		}
	}
}
