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
package org.jenkinsci.plugins.isolationctx.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import hudson.Proc;
import hudson.model.TaskListener;
import hudson.FilePath;
import hudson.Launcher.ProcStarter;

public class WorkspaceLauncherUtil {

	protected final Supplier<FilePath> workspaceSupplier;
	protected final Supplier<TaskListener> listenerSupplier;

	public WorkspaceLauncherUtil(final FilePath workspace, final TaskListener listener) {
		this(() -> workspace, () -> listener);
	}

	public WorkspaceLauncherUtil(final Supplier<FilePath> workspaceSupplier,
			final Supplier<TaskListener> listenerSupplier) {
		this.workspaceSupplier = workspaceSupplier;
		this.listenerSupplier = listenerSupplier;
	}

	public FilePath getWorkspace() {
		return this.workspaceSupplier.get();
	}

	public TaskListener logListener() {
		return this.listenerSupplier.get();
	}

	public Proc wsExecAndJoinCmd(final List<String> cmd, final boolean readStderr)
			throws IOException, InterruptedException {
		return wsExecAndJoinCmd(cmd, readStderr, 0);
	}

	public Proc wsExecAndJoinCmd(final List<String> cmd, final boolean readStderr, final int expectedExitCode)
			throws IOException, InterruptedException {
		final ProcStarter procStarter = this.getWorkspace()
				.createLauncher(logListener())
				.launch()
				.cmds(cmd)
				.readStdout();
		if (readStderr)
			procStarter.readStderr();

		final Proc proc = procStarter.start();
		if (readStderr)
			joinAndLogProcessException(proc, expectedExitCode);
		else
			proc.join();
		return proc;
	}

	public void joinAndLogProcessException(final Proc process, final int expectedExitCode)
			throws IOException, InterruptedException {
		final PrintStream nodeLog = logListener().getLogger();
		final int exitCode = process.join();
		if (exitCode == expectedExitCode)
			return;
		nodeLog.println("exitCode=" + exitCode);
		try {
			final InputStream errStream = process.getStderr();
			if (errStream == null)
				return;
			errStream.transferTo(nodeLog);
		} catch (IOException e) {
			e.printStackTrace(nodeLog);
		}
	}

	public static String outputToString(final Proc proc) throws CompletionException {
		try {
			return new String(proc.getStdout()
					.readAllBytes()).trim();
		} catch (IOException e) {
			throw new CompletionException(e);
		}
	}

	public static long outputToLong(final Proc proc, final int radix) throws CompletionException {
		return toLong(outputToString(proc), radix);
	}

	public static long toLong(final String string, final int radix) throws CompletionException {
		try {
			return Long.parseLong(string, radix);
		} catch (NumberFormatException e) {
			throw new CompletionException(e);
		}
	}
}
