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
package org.jenkinsci.plugins.ctx.proxy.repo;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import hudson.model.TaskListener;
import net.runeduniverse.lib.utils.net.api.IpAddress;

public interface ProxyInstance {

	public <T extends IpAddress<T>> void bind(IpAddress<T> ip, Map<String, String> proxyEnv, TaskListener listener);

	public CompletableFuture<?> onStop();

}
