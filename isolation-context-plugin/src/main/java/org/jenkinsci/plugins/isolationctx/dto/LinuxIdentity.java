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
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.jenkinsci.plugins.isolationctx.util.WorkspaceLauncherUtil;

import static org.jenkinsci.plugins.isolationctx.util.WorkspaceLauncherUtil.outputToString;
import static org.jenkinsci.plugins.isolationctx.util.WorkspaceLauncherUtil.outputToLong;
import static org.jenkinsci.plugins.isolationctx.util.WorkspaceLauncherUtil.toLong;

public class LinuxIdentity implements Serializable {

	private static final long serialVersionUID = 1L;

	protected final Set<Long> groupIds = new HashSet<>();
	protected long userId = -1;
	protected long groupId = -1;

	public long getUserId() {
		synchronized (this) {
			return this.userId;
		}
	}

	public long getGroupId() {
		synchronized (this) {
			return this.groupId;
		}
	}

	public Set<Long> getGroupIds() {
		synchronized (this) {
			return this.groupIds;
		}
	}

	public LinuxIdentity setUserId(final long user) {
		synchronized (this) {
			this.userId = user;
		}
		return this;
	}

	public LinuxIdentity setGroupId(final long group) {
		synchronized (this) {
			this.groupId = group;
		}
		return this;
	}

	public LinuxIdentity setGroupIds(final Collection<Long> groups) {
		synchronized (this) {
			this.groupIds.clear();
			addGroupIds(groups);
		}
		return this;
	}

	public LinuxIdentity addGroupIds(final Long... groups) {
		synchronized (this) {
			if (groups == null || groups.length == 0)
				return this;
			addGroupIds(Arrays.asList(groups));
		}
		return this;
	}

	public LinuxIdentity addGroupIds(final Collection<Long> groups) {
		synchronized (this) {
			if (groups == null || groups.isEmpty())
				return this;
			groups.stream()
					.filter(Objects::nonNull)
					.forEach(this.groupIds::add);
		}
		return this;
	}

	public LinuxIdentity removeGroupIds(final Long... groups) {
		synchronized (this) {
			if (groups == null || groups.length == 0)
				return this;
			addGroupIds(Arrays.asList(groups));
		}
		return this;
	}

	public LinuxIdentity removeGroupIds(final Collection<Long> groups) {
		synchronized (this) {
			if (groups == null || groups.isEmpty())
				return this;
			groups.stream()
					.filter(Objects::nonNull)
					.forEach(this.groupIds::remove);
		}
		return this;
	}

	public String joinGroupIds(final CharSequence delimiter) {
		synchronized (this) {
			final Iterator<Long> i = this.groupIds.iterator();
			if (!i.hasNext())
				return "";
			final StringBuilder builder = new StringBuilder(Long.toString(i.next()));
			while (i.hasNext()) {
				builder.append(delimiter)
						.append((long) i.next());
			}
			return builder.toString();
		}
	}

	public LinuxIdentity copy() {
		synchronized (this) {
			final LinuxIdentity identity = new LinuxIdentity();
			identity.setUserId(this.userId);
			identity.setGroupId(this.groupId);
			identity.setGroupIds(this.groupIds);
			return identity;
		}
	}

	public static LinuxIdentity identify(final WorkspaceLauncherUtil launcherUtil) throws CompletionException {
		final LinuxIdentity id = new LinuxIdentity();
		try {
			// get user
			id.setUserId(outputToLong(launcherUtil.wsExecAndJoinCmd(Arrays.asList("id", "-r", "--user"), true), 10));
			// get group
			id.setGroupId(outputToLong(launcherUtil.wsExecAndJoinCmd(Arrays.asList("id", "-r", "--group"), true), 10));
			// get groups
			Arrays.stream(outputToString(launcherUtil.wsExecAndJoinCmd(Arrays.asList("id", "-r", "--groups"), true))
					.split(" "))
					.filter(s -> s != null && 0 < s.length())
					.map(s -> toLong(s, 10))
					.forEach(id.groupIds::add);
		} catch (IOException | InterruptedException e) {
			throw new CompletionException(e);
		}
		return id;
	}

	public static long groupIdByName(final WorkspaceLauncherUtil launcherUtil, final String groupName)
			throws CompletionException {
		if (groupName == null || groupName.length() == 0)
			return -1;
		try {
			final String line = outputToString(
					launcherUtil.wsExecAndJoinCmd(Arrays.asList("getent", "group", groupName), true));
			// $ getent group users
			// users:x:100:<user>
			final String[] data = line.split(" ", 4);
			return toLong(data[2], 10);
		} catch (IndexOutOfBoundsException | IOException | InterruptedException e) {
			throw new CompletionException(e);
		}
	}
}