/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Frederik Fromm
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.buildblocker;

import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.StringParameterValue;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.tasks.Shell;
import java.util.ArrayList;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests
 */
public class BlockingJobsMonitorTest extends HudsonTestCase {

    /**
     * One test for all for faster execution
     *
     * @throws Exception
     */
    public void testConstructor() throws Exception {
        // clear queue from preceding tests
        Jenkins.getInstance().getQueue().clear();

        // init slave
        LabelAtom label = new LabelAtom("label");
        DumbSlave slave = this.createSlave(label);
        SlaveComputer c = slave.getComputer();
        c.connect(false).get(); // wait until it's connected
        if (c.isOffline()) {
            fail("Slave failed to go online: " + c.getLog());
        }

        String blockingJobName = "blockingJob";
        String blockingEnvVar = "branchName";

        FreeStyleProject blockingProject = this.createFreeStyleProject(blockingJobName);
        blockingProject.setAssignedLabel(label);

        Shell shell = new Shell("sleep 2");
        blockingProject.getBuildersList().add(shell);
        ArrayList<ParameterValue> values = new ArrayList<ParameterValue>();
        values.add(new StringParameterValue("blockingEnvVars", "branchName"));
        values.add(new StringParameterValue("GIT_BRANCH", ""));
        values.add(new StringParameterValue("branchName", "someBlockingBranch"));
        Future<FreeStyleBuild> future = blockingProject.scheduleBuild2(1, new UserCause(), new ParametersAction(values));

        // wait until blocking job started
        while (!slave.getComputer().getExecutors().get(0).isBusy()) {
            TimeUnit.SECONDS.sleep(1);
        }

        BlockingJobsMonitor blockingJobsMonitorUsingNull = new BlockingJobsMonitor(null, null);
        assertNull(blockingJobsMonitorUsingNull.getBlockingJob(null));

        BlockingJobsMonitor blockingJobsMonitorNotMatching = new BlockingJobsMonitor("xxx", null);
        assertNull(blockingJobsMonitorNotMatching.getBlockingJob(null));

        BlockingJobsMonitor blockingJobsMonitorUsingFullName = new BlockingJobsMonitor(blockingJobName, null);
        assertEquals(blockingJobName, blockingJobsMonitorUsingFullName.getBlockingJob(null).getDisplayName());

        BlockingJobsMonitor blockingJobsMonitorUsingRegex = new BlockingJobsMonitor("block.*", null);
        assertEquals(blockingJobName, blockingJobsMonitorUsingRegex.getBlockingJob(null).getDisplayName());

        BlockingJobsMonitor blockingJobsMonitorUsingMoreLines = new BlockingJobsMonitor("xxx\nblock.*\nyyy", null);
        assertEquals(blockingJobName, blockingJobsMonitorUsingMoreLines.getBlockingJob(null).getDisplayName());

        BlockingJobsMonitor blockingJobsMonitorUsingWrongRegex = new BlockingJobsMonitor("*BW2S.*QRT.", null);
        assertNull(blockingJobsMonitorUsingWrongRegex.getBlockingJob(null));

        //blockingBranch
        FreeStyleProject blockedProject = this.createFreeStyleProject("random");
        Future<FreeStyleBuild> future3 = blockedProject.scheduleBuild2(2, new UserCause(), new ParametersAction(values));
        Queue.Item q = hudson.getQueue().getItem(blockedProject);

        BlockingJobsMonitor blockingBranchMonitorUsingNull = new BlockingJobsMonitor(null, null);
        assertNull(blockingBranchMonitorUsingNull.getBlockingJob(q));

        BlockingJobsMonitor blockingBranchMonitorNotMatching = new BlockingJobsMonitor(null, "xxx");
        assertNull(blockingBranchMonitorNotMatching.getBlockingJob(q));

        BlockingJobsMonitor blockingBranchMonitorUsingFullName = new BlockingJobsMonitor(null, blockingEnvVar);
        assertEquals(blockingJobName, blockingBranchMonitorUsingFullName.getBlockingJob(q).getDisplayName());

        BlockingJobsMonitor blockingBranchMonitorUsingMoreLines = new BlockingJobsMonitor(null, "xxx\n"+blockingEnvVar+"\nyyy");
        assertEquals(blockingJobName, blockingBranchMonitorUsingMoreLines.getBlockingJob(q).getDisplayName());

        BlockingJobsMonitor blockingBranchMonitorUsingWrongRegex = new BlockingJobsMonitor(null, "*BW2S.*QRT.");
        assertNull(blockingBranchMonitorUsingWrongRegex.getBlockingJob(q));

        // wait until blocking job stopped
        while (!future.isDone()) {
            TimeUnit.SECONDS.sleep(1);
        }

        assertNull(blockingJobsMonitorUsingFullName.getBlockingJob(null));
    }
}
