package org.jenkinsci.plugins.dbm;

import hudson.FilePath;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


public class ProducerConsumerCategory extends AbstractCategory {

    public ProducerConsumerCategory(String branch, String category, List<String> projects, String version,
            boolean includeHardware, String startTime, String daysToSaveLogs, String branchUrl,
            String nextSystemBuildNumber, LinkedList<JSFeedback> messages, boolean hotfix) {
        super(branch, category, projects, version, includeHardware, startTime, daysToSaveLogs, branchUrl,
                nextSystemBuildNumber, messages, hotfix);
    }

    @Override
    public boolean createProjectList(List<String> projects) {
        try {
            FilePath categoryPath = getCategoryPath();
            if (!categoryPath.child("Producer.xml").exists()) {
                return false;
            }
            this.getProjects().add(new JenkinsProject("Producer", true));
            for (String project : projects) {
                this.getProjects().add(new JenkinsProject(project, false));
            }
            return true;
        } catch (IOException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "createProjectList", e);
        } catch (InterruptedException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "createProjectList", e);
        }
        return false;
    }

    @Override
    public FilePath getTemplate(JenkinsProject project) throws IOException, InterruptedException {
        FilePath template = null;
        if (project.getName().equalsIgnoreCase("producer")) {
            template = this.getCategoryPath().child(project.getName() + ".xml");
            return (template.exists()) ? template : null;
        } else {
            for (FilePath path : getCategoryPath().list()) {
                if (path.isDirectory()) {
                    template = path.child(project.getName() + ".xml");
                    if (template.exists()) {
                        return template;
                    }
                }
            }
        }
        return null;
    }

}
