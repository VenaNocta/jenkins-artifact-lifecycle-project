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
package org.jenkinsci.plugins.repoproxy.config;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

import hudson.model.TaskListener;

public abstract class SimpleFileVisitor implements FileVisitor<Path> {

	protected final TaskListener taskListener;

	public SimpleFileVisitor(final TaskListener taskListener) {
		this.taskListener = Objects.requireNonNull(taskListener);
	}

	public TaskListener getTaskListener() {
		return this.taskListener;
	}

	protected Throwable unwrap(final IOException exc) {
		return exc;
	}

	protected boolean log(final IOException exc) {
		final Throwable t = unwrap(exc);
		if (t != null) {
			t.printStackTrace(this.taskListener.getLogger());
			return true;
		}
		return false;
	}

	@Override
	public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
		// skip hidden directories like .git
		if (dir.getFileName()
				.toString()
				.startsWith("."))
			return FileVisitResult.SKIP_SUBTREE;
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
		if (log(exc))
			throw exc;
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
		if (log(exc))
			throw exc;
		return FileVisitResult.CONTINUE;
	}
}
