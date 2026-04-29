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
package org.jenkinsci.plugins.ctx.isolation.converters;

import com.thoughtworks.xstream.XStream;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import hudson.model.Run;
import hudson.util.XStream2;
import jenkins.model.Jenkins;

public class PluginInitializer {

	@Initializer(before = InitMilestone.SYSTEM_CONFIG_LOADED)
	public static void registerXStreamForNodes() {

		System.out.println("==================== HERE!");

		XStream2[] streams = new XStream2[] { Jenkins.XSTREAM2, Items.XSTREAM2, Run.XSTREAM2 };

		for (XStream xs : streams) {
			xs.allowTypesByWildcard(new String[] { "net.runeduniverse.lib.utils.net.*" });

			xs.registerConverter(new Ip4AddressConverter());
			xs.registerConverter(new Ip6AddressConverter());
			xs.registerConverter(new IpNet4AddressConverter());
			xs.registerConverter(new IpNet6AddressConverter());
		}
	}
}
