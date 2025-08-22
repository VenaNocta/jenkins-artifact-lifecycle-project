/*
 * Copyright © 2025 VenaNocta (venanocta@gmail.com)
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
package org.jenkinsci.plugins.artifact.deploy.context;

import java.io.IOException;
import java.io.Serializable;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;

public class BundleUtils {

	public static void indexArtifacts(@NonNull final Bundle bundle, final ArtifactManager artifactManager,
			@NonNull final String includes, @CheckForNull final String excludes, final boolean useDefaultExcludes,
			final Map<String, Serializable> domMetadata) throws IOException {
		Objects.requireNonNull(bundle, "Bundle must not be Null!");

		findArtifacts(artifactManager, includes, excludes, useDefaultExcludes, (artifactFile, metadata) -> {
			// override with specified values
			if (domMetadata != null)
				metadata.putAll(domMetadata);
			// index artifact
			bundle.indexArtifact(artifactFile, metadata);
		});
	}

	public static void findArtifacts(final ArtifactManager artifactManager, @NonNull final String includes,
			@CheckForNull final String excludes, final boolean useDefaultExcludes,
			final BiConsumer<VirtualFile, Map<String, Serializable>> artifactHandler) throws IOException {
		Objects.requireNonNull(artifactManager, "ArtifactManager must not be Null!");
		Objects.requireNonNull(artifactHandler, "ArtifactHandler (BiConsumer) must not be Null!");

		final VirtualFile archiveFileRoot = artifactManager.root();

		for (String filePath : archiveFileRoot.list(includes, excludes, useDefaultExcludes)) {
			final VirtualFile artifactFile = archiveFileRoot.child(filePath);
			if (!artifactFile.isFile())
				continue;
			final Map<String, Serializable> metadata = new HashMap<>(3);
			// set base values derived from file
			metadata.put(Bundle.METADATA_FILE_PATH, filePath);
			metadata.put(Bundle.METADATA_FILE_NAME, artifactFile.getName());
			metadata.put(Bundle.METADATA_FILE_EXTENSION, getFileExtension(artifactFile.getName()));
			// process artifact
			artifactHandler.accept(artifactFile, metadata);
		}
	}

	@NonNull
	public static String getFileExtension(final String fileName) {
		if (fileName == null)
			return "";
		final int idx = fileName.indexOf('.');
		if (idx == -1)
			return "";
		return fileName.substring(idx + 1);
	}

	public static <S extends Serializable> S getMetadataEntry(final Map<String, Serializable> metadata,
			final Class<S> type, final String... keys) {
		for (String key : keys) {
			final Serializable obj = metadata.get(key);
			if (type.isInstance(obj))
				return (S) obj;
		}
		return null;
	}

	public static String fullPath(@NonNull final VirtualFile root, @NonNull final VirtualFile file,
			@CheckForNull final Function<String, String> nameMapper) {
		Objects.requireNonNull(root, "VirtualFile 'archive root folder' must not be Null!");
		Objects.requireNonNull(file, "VirtualFile 'archive file' must not be Null!");

		final Deque<String> path = new LinkedList<>();
		// compute the file name
		if (nameMapper == null)
			path.add(file.getName());
		else
			path.add(nameMapper.apply(file.getName()));
		// rebuild the full path, like a chroot to the root folder
		for (VirtualFile f = file.getParent(); f != null; f = f.getParent()) {
			if (root.equals(f))
				break;
			path.addFirst(f.getName());
		}
		return String.join("/", path);
	}

}
