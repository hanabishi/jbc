package org.jenkinsci.plugins.jbm;

import hudson.FilePath;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class DefaultCategory extends AbstractCategory {

    public DefaultCategory(String branch, String category, List<String> projects, String version,
            boolean includeHardware, String startTime, String daysToSaveLogs, String branchUrl,
            String nextSystemBuildNumber, LinkedList<JSFeedback> messages, boolean hotfix) {
        super(branch, category, projects, version, includeHardware, startTime, daysToSaveLogs, branchUrl,
                nextSystemBuildNumber, messages, hotfix);
    }

    @Override
    public boolean createProjectList(List<String> projects) {
        for (String project : projects) {
            this.getProjects().add(new JenkinsProject(project, true));
        }
        return true;
    }

    @Override
    public FilePath getTemplate(JenkinsProject project) throws IOException, InterruptedException {
        FilePath template = this.getCategoryPath().child(project.getName() + ".xml");
        return (template.exists()) ? template : null;
    }

}
