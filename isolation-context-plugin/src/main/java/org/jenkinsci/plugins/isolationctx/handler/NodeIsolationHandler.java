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
package org.jenkinsci.plugins.isolationctx.handler;

import java.io.IOException;
import org.jenkinsci.plugins.isolationctx.dto.TrafficMap;

import hudson.model.TaskListener;

public interface NodeIsolationHandler {

	public boolean isActive();

	public TaskListener logTaskListener();

	public void setup(TaskListener listener) throws IOException, InterruptedException;

	public void shutdown(TaskListener listener) throws IOException, InterruptedException;

	public void firewallInputOpen(TrafficMap data, TaskListener listener) throws IOException, InterruptedException;

	public void firewallInputRetainOpen(TrafficMap data, TaskListener listener)
			throws IOException, InterruptedException;

	public void firewallInputClose(TrafficMap data, TaskListener listener) throws IOException, InterruptedException;

	public void firewallInputCloseAll(TaskListener listener) throws IOException, InterruptedException;

}
