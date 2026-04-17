package org.jenkinsci.plugins.airgap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hudson.Extension;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.Run;

@Extension
public class AirgapLauncherDecorator extends LauncherDecorator {

    @Override
    public Launcher decorate(Launcher launcher, Node node) {

        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {

                Run<?,?> run = AirgapRunContext.getCurrentRun();
                if (run == null) {
                    return super.launch(starter);
                }

                SandboxAction action = run.getAction(SandboxAction.class);
                if (action == null) {
                    return super.launch(starter);
                }

                SandboxInstance sandbox = action.getSandbox();

                // Wrap command with nsenter
                List<String> cmd = new ArrayList<>();
                cmd.add("nsenter");
                cmd.add("--target=" + sandbox.getPid());
                cmd.add("--mount");
                cmd.add("--net");
                cmd.add("--pid");
                cmd.add("--ipc");
                cmd.add("--uts");
                cmd.add("--");

                cmd.addAll(starter.cmds());

                starter.cmds(cmd);

                return super.launch(starter);
            }
        };
    }
}
