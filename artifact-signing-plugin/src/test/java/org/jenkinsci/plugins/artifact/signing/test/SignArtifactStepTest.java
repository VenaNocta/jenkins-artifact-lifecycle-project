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
package org.jenkinsci.plugins.artifact.signing.test;

import hudson.model.Label;
import java.io.File;
import java.io.IOException;

import org.jenkinsci.plugins.pgpcredentials.api.PGPPrivateKeyCredentials;
import org.jenkinsci.plugins.pgpcredentials.test.APGPTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

public class SignArtifactStepTest extends APGPTest {

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	protected static final String ED25519_PRIVATE_KEY_ID = "ed25519-pgp-key";

	protected File armoredKey;
	protected PGPPrivateKeyCredentials ed25519_privateKeyCred;

	@Before
	public void setup() throws IOException {
		setupFile_ed25519_privateKey(this.armoredKey = tmp.newFile("ed25519-private-key.asc"));
		this.ed25519_privateKeyCred = load_privateKeyCredentials(CredentialsScope.GLOBAL, ED25519_PRIVATE_KEY_ID,
				ED25519_PASSPHRASE, this.armoredKey);
	}

	@Test
	public void testScriptedPipeline() throws Exception {
		String agentLabel = "my-agent";
		jenkins.createOnlineSlave(Label.get(agentLabel));
		WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-scripted-pipeline");

		SystemCredentialsProvider.getInstance()
				.getCredentials()
				.add(this.ed25519_privateKeyCred);

		String pipelineScript = "node {\n" //
				+ "writeFile file: 'file.jar', text: 'xxx' \n" //
				+ "archiveArtifacts artifacts: '*', fingerprint: true\n" //
				+ "signArtifacts artifacts: '*', credentialId: '" + ED25519_PRIVATE_KEY_ID + "'\n" //
				+ "}";
		job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
		WorkflowRun completedBuild = jenkins.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}
}
