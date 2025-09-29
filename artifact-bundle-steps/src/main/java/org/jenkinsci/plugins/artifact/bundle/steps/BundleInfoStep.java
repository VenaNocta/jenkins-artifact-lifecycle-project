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
package org.jenkinsci.plugins.artifact.bundle.steps;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import org.jenkinsci.plugins.artifact.bundle.api.context.Bundle;
import org.jenkinsci.plugins.artifact.bundle.api.context.BundleContext;
import org.jenkinsci.plugins.artifact.bundle.api.context.Bundles;
import org.jenkinsci.plugins.artifact.bundle.api.steps.BundleStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import jenkins.util.VirtualFile;
import net.runeduniverse.lib.utils.logging.log.SortedCompoundTree;
import net.runeduniverse.lib.utils.logging.log.api.CompoundTree;
import net.runeduniverse.lib.utils.logging.log.api.CompoundTreeStyle;

public class BundleInfoStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	private String bundle;
	private boolean metadata = false;

	@DataBoundConstructor
	public BundleInfoStep() {
	}

	public String getBundle() {
		return this.bundle;
	}

	public boolean getMetadata() {
		return this.metadata;
	}

	@DataBoundSetter
	public void setBundle(final String bundle) {
		this.bundle = bundle;
	}

	@DataBoundSetter
	public void setMetadata(final boolean metadata) {
		this.metadata = metadata;
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new BundleInfoExecution(context, this);
	}

	public static class BundleInfoExecution extends BundleStepExecution {

		private static final long serialVersionUID = 1L;

		private final BundleInfoStep step;

		private CompoundTreeStyle style = null;

		public BundleInfoExecution(final StepContext context, final BundleInfoStep step) {
			super(context);
			this.step = step;
		}

		protected CompoundTreeStyle getStyle() {
			if (this.style == null) {
				this.style = new CompoundTreeStyle() {
					@Override
					public CharSequence getInitialOffset() {
						return "";
					}

					@Override
					public CharSequence getNoElementOffset() {
						return "  ";
					}

					@Override
					public CharSequence getEmptyElementOffset() {
						return "| ";
					}

					@Override
					public CharSequence getElementOffset() {
						return "├ ";
					}

					@Override
					public CharSequence getLastElementOffset() {
						return "└ ";
					}

					@Override
					public boolean getTagsToUpper() {
						return false;
					}
				};
			}
			return this.style;
		}

		public CompoundTree createTree(final CharSequence tag, final CharSequence value) {
			return new SortedCompoundTree(tag, value, getStyle());
		}

		@Override
		public boolean start() throws Exception {
			final BundleContext context = getBundleContext();

			final boolean skipMetadata = !this.step.getMetadata();
			final Bundle bundle = context.getBundleOrDefault(this.step.getBundle());

			final CompoundTree bundleTree = createTree(null, String.format("Artifacts in Bundle (%s)", bundle.getId()));

			for (Iterator<Entry<VirtualFile, Map<String, Serializable>>> i = bundle.getArtifactIterator(); i
					.hasNext();) {
				final Entry<VirtualFile, Map<String, Serializable>> entry = i.next();
				final Map<String, Serializable> metadata = entry.getValue();
				final String path = skipMetadata ? getEntryString(metadata, Bundle.METADATA_FILE_PATH) : null;
				final CompoundTree artifactTree = new SortedCompoundTree(path == null ? entry.getKey()
						.getName() : path, getStyle());
				bundleTree.append(artifactTree);

				if (skipMetadata)
					continue;

				for (Entry<String, Serializable> dataEntry : metadata.entrySet()) {
					Bundles.indexMetadata(artifactTree, this::createTree, dataEntry.getKey(), dataEntry.getValue());
				}
			}

			for (String text : bundleTree.toText()) {
				log(Level.INFO, text, null);
			}

			getContext().onSuccess(null);
			return true;
		}

	}

	@Extension
	public static class BundleInfoDescriptor extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "bundleInfo";
		}

		@Override
		public String getDisplayName() {
			return Messages.BundleInfo_DisplayName();
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return Set.of(BundleContext.class);
		}

		@Override
		public Set<? extends Class<?>> getProvidedContext() {
			return Set.of();
		}

	}

}
