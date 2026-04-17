package org.jenkinsci.plugins.airgap;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class AirgapRunListener extends RunListener<Run<?,?>> {

    @Override
    public void onStarted(Run<?,?> run, TaskListener listener) {
        if (!isAirgapEnabled(run)) {
            return;
        }

        SandboxInstance sandbox = SandboxManager.startSandbox(run, listener);
        run.addAction(new SandboxAction(sandbox));
    }

    @Override
    public void onCompleted(Run<?,?> run, TaskListener listener) {
        SandboxAction action = run.getAction(SandboxAction.class);
        if (action != null) {
            SandboxManager.stopSandbox(action.getSandbox());
        }
    }

    private boolean isAirgapEnabled(Run<?,?> run) {
        Job<?,?> job = run.getParent();
        AirgapJobProperty prop = job.getProperty(AirgapJobProperty.class);
        return prop != null && prop.isEnabled();
    }
}
