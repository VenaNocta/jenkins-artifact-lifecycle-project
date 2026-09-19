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
package org.jenkinsci.plugins.artifact.lifecycle.config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ArtifactRepositoryAdapterBuilder<T extends ArtifactRepository> {

	protected final Map<Class<?>, Object> handler = new HashMap<>();

	protected final T original;

	public ArtifactRepositoryAdapterBuilder(final T original) {
		this.original = Objects.requireNonNull(original, "ArtifactRepository must not be null");
	}

	public T getOriginal() {
		return this.original;
	}

	public ArtifactRepositoryAdapterBuilder<T> withHandler(final Object handler, final Class<?>... interfaces) {
		if (interfaces == null)
			return this;
		final Set<Class<?>> types = new HashSet<>();
		for (Class<?> type : interfaces) {
			if (type == null || !type.isInterface())
				continue;
			types.add(type);
		}
		if (types.isEmpty())
			return this;
		for (Class<?> type : types)
			this.handler.put(type, handler);
		return this;
	}

	public ArtifactRepositoryInvocationHandler buildInvocationHandler() {
		return new ArtifactRepositoryInvocationHandler(this.original, Collections.unmodifiableMap(this.handler));
	}

	public ArtifactRepository buildProxy() {
		final ArtifactRepositoryInvocationHandler handler = buildInvocationHandler();
		return (ArtifactRepository) Proxy.newProxyInstance(this.original.getClass()
				.getClassLoader(), handler.getInterfaces(), handler);
	}

	public static class ArtifactRepositoryInvocationHandler implements InvocationHandler {

		protected final ArtifactRepository original;
		protected final Map<Class<?>, Object> handler;
		protected final Map<Method, Object> methodHandlerMapper;

		public ArtifactRepositoryInvocationHandler(final ArtifactRepository original,
				final Map<Class<?>, Object> handler) {
			this.original = Objects.requireNonNull(original, "ArtifactRepository must not be null");
			this.handler = Objects.requireNonNull(handler, "handler-map must not be null");
			this.methodHandlerMapper = computeMapper(original, handler);
		}

		public ArtifactRepositoryInvocationHandler(final ArtifactRepository original,
				final Map<Class<?>, Object> handler, final Map<Method, Object> methodHandlerMapper) {
			this.original = Objects.requireNonNull(original, "ArtifactRepository must not be null");
			this.handler = Objects.requireNonNull(handler, "handler-map must not be null");
			this.methodHandlerMapper = methodHandlerMapper;
		}

		public Class<?>[] getInterfaces() {
			return getInterfaceSet().toArray(new Class<?>[0]);
		}

		public Set<Class<?>> getInterfaceSet() {
			final Set<Class<?>> set = new HashSet<>(this.handler.keySet());
			for (Class<?> i : this.original.getClass()
					.getInterfaces())
				set.add(i);
			return set;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
			if (this.methodHandlerMapper.containsKey(method))
				return method.invoke(this.methodHandlerMapper.get(method), args);
			throw new RuntimeException("Interface for Method<" + method + "> not found!");
		}

		protected static Map<Method, Object> computeMapper(final ArtifactRepository original,
				final Map<Class<?>, Object> handler) {
			final Map<Method, Object> mapper = new HashMap<>();
			// PUT ORDER > LAST ADDED => PERSISTS
			// 1. exten
			for (Class<?> clazz : handler.keySet()) {
				final Object methodHandler = handler.get(clazz);
				for (Method m : clazz.getMethods())
					mapper.put(m, methodHandler);
			}
			// 2. adapter Object
			for (Method m : original.getClass()
					.getMethods())
				mapper.put(m, original);
			// 3. adapter Interfaces [ArtifactRepository,...]
			for (Class<?> clazz : original.getClass()
					.getInterfaces()) {
				for (Method m : clazz.getMethods())
					mapper.put(m, original);
			}
			return mapper;
		}
	}
}
