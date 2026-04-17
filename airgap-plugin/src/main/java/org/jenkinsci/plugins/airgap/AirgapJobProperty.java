package org.jenkinsci.plugins.airgap;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;

public class AirgapJobProperty extends JobProperty<Job<?,?>> {

    private final boolean enabled;

    @DataBoundConstructor
    public AirgapJobProperty(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Force Airgap Sandbox";
        }
    }
}
