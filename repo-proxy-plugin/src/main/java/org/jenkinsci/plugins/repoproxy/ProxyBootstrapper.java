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
package org.jenkinsci.plugins.repoproxy;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jenkinsci.plugins.isolationctx.IsolationContext.NetLink;
import org.jenkinsci.plugins.repoproxy.config.BomSourceCache;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.common.IdCredentials;

import hudson.model.TaskListener;
import jenkins.agents.ControllerToAgentFileCallable;

public interface ProxyBootstrapper {

	public Set<UUID> getRequiredBoms();

	public Set<String> getRequiredCredentials();

	public ControllerToAgentFileCallable<UUID> serverStartCallable(NetLink link, //
			StepContext context, Map<String, String> proxyEnv, //
			Map<UUID, BomSourceCache> bomCacheMap, Map<String, IdCredentials> credentialMap, //
			TaskListener listener) throws IOException, InterruptedException;

	public void receiveCallback(String refKey, Serializable data);

}
