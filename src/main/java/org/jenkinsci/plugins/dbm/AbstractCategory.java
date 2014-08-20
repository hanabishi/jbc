package org.jenkinsci.plugins.dbm;

import hudson.FilePath;
import hudson.model.TopLevelItem;
import hudson.model.AbstractProject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import jenkins.model.Jenkins;

import org.apache.commons.io.IOUtils;

import com.thoughtworks.xstream.mapper.CannotResolveClassException;

public abstract class AbstractCategory {

    public static class JSFeedback {
        public int code = 0;
        public String message = "";

        public JSFeedback() {
        }

        public JSFeedback(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
    
    public static Random rnd = new Random();

    private String category = "";
    private HashMap<String, String> defeaultParameters = new HashMap<String, String>();
    private List<JenkinsProject> projects = new LinkedList<JenkinsProject>();
    private String version;
    private boolean includeHardware;
    private boolean validConfig = false;
    private String branch;
    private String startTime;
    private String daysToSaveLogs;
    private String branchUrl;
    private String nextSystemBuildNumber;

    private LinkedList<JSFeedback> messages;

    private boolean hotfix;

    public AbstractCategory(String branch, String category, List<String> projects, String version,
            boolean includeHardware, String startTime, String daysToSaveLogs, String branchUrl,
            String nextSystemBuildNumber, LinkedList<JSFeedback> messages, boolean hotfix) {
        this.daysToSaveLogs = daysToSaveLogs;
        this.branchUrl = branchUrl;
        this.nextSystemBuildNumber = nextSystemBuildNumber;
        this.messages = messages;
        this.setHotfix(hotfix);
        this.setBranch(branch);
        this.category = category;
        this.setVersion(version);
        this.setIncludeHardware(includeHardware);
        this.startTime = startTime;
        setValidConfig(createProjectList(projects));
        updateGlobalParameters();
    }

    public abstract boolean createProjectList(List<String> projects);

    public String substituteNotification(String xmlData, String projectName) {
        xmlData = xmlData.replaceAll(Pattern.quote("[NOTIFY-ON-SUCCESS]"), Matcher.quoteReplacement(""));
        xmlData = xmlData.replaceAll(Pattern.quote("[SUCCESS-RECIPIENT-LIST]"), Matcher.quoteReplacement(""));
        xmlData = xmlData.replaceAll(Pattern.quote("[FAILED-RECIPIENT-LIST]"), Matcher.quoteReplacement(""));

        return xmlData;
    }

    public String getMajorVersion() {
        return version.substring(0, version.lastIndexOf('.'));
    }

    public HashMap<String, String> getParameterDict() {
        String[] split = startTime.split(Pattern.quote(":"));

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("DESCRIPTION", "-");
        map.put("DAYS-TO-SAVE", daysToSaveLogs);
        map.put("SVNURL", branchUrl);
        map.put("BRANCH-LOWER", branch.toLowerCase());
        map.put("BRANCH", branch);
        map.put("AS-VERSION", version.substring(0, version.lastIndexOf('.')));
        map.put("START-NUMBER", nextSystemBuildNumber);
        map.put("START-TIME-SPECIFIC", split[1] + " " + split[0] + " * * *");
        map.put("START-TIME-CONTINUOUS", (rnd.nextInt(15)) + "," + (rnd.nextInt(15) + 15) + ","
                + (rnd.nextInt(15) + 30) + "," + (rnd.nextInt(15) + 45) + " * * * *");
        map.put("START-NUMBER", nextSystemBuildNumber);
        if (this.includeHardware) {
            map.put("SYSTEM-DEVICE", "," + branch + "-3-System-Integrationtest-Device");
        } else {
            map.put("SYSTEM-DEVICE", "");
        }
        map.put("CHILDREN", getChildrenString());
        return map;
    }

    private void updateGlobalParameters() {
        try {
            getDefeaultParameters().clear();
            for (FilePath path : TemplateManager.getInstance().getSvnTemplateFolder().list()) {
                if (!path.isDirectory() && !path.getName().equalsIgnoreCase("view.xml")
                        && !path.getName().equalsIgnoreCase("template.xml")) {
                    getDefeaultParameters().put(path.getName().replaceAll(Pattern.quote(".xml"), ""),
                            path.readToString());
                }
            }
        } catch (IOException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "updateGlobalParameters", e);
        } catch (InterruptedException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "updateGlobalParameters", e);
        }
    }

    public String getChildrenString() {
        String children = "";
        for (JenkinsProject project : this.getProjects()) {
            if (!project.isRootProject()) {
                if (!children.isEmpty()) {
                    children = children.concat(",");
                }
                children = children.concat(this.getProjectName(project));
            }
        }

        return children;
    }

    public abstract FilePath getTemplate(JenkinsProject project) throws IOException, InterruptedException;

    public void buildXmlConfiguration() throws IOException, InterruptedException {
        FilePath categoryPath = this.getCategoryPath();
        if (!categoryPath.exists()) {
            return;
        }
        for (JenkinsProject project : this.getProjects()) {
            FilePath template = getTemplate(project);
            if (template == null) {
                return;
            }
            project.setXmlTemplate(replaceTemplateData(template.readToString(), project.getName()));
        }
    }

    public String replaceTemplateData(String xmlData, String projectName) {
        for (Map.Entry<String, String> entry : defeaultParameters.entrySet()) {
            xmlData = xmlData.replaceAll(Pattern.quote("[" + entry.getKey() + "]"),
                    Matcher.quoteReplacement(entry.getValue()));
        }
        HashMap<String, String> parameterDict = getParameterDict();
        for (Map.Entry<String, String> entry : parameterDict.entrySet()) {
            xmlData = xmlData.replaceAll(Pattern.quote("[" + entry.getKey() + "]"),
                    Matcher.quoteReplacement(entry.getValue()));
        }

        return substituteNotification(xmlData, projectName);
    }

    public String getProjectName(JenkinsProject project) {
        return getBranch() + "-" + category + "-" + project.getName();
    }

    @SuppressWarnings("rawtypes")
    public boolean createProjects() throws IOException, InterruptedException {
        buildXmlConfiguration();
        for (JenkinsProject project : getProjects()) {
            InputStream inputStream = null;
            try {
                inputStream = new ByteArrayInputStream(project.getXmlTemplate().getBytes());
                StreamSource source = new StreamSource(inputStream);
                for (AbstractProject absProject : Jenkins.getInstance().getItems(AbstractProject.class)) {
                    if (absProject.getName().equalsIgnoreCase(project.getName())) {
                        absProject.updateByXml((Source) source);
                        messages.add(new JSFeedback(0, "Successfully updated " + getProjectName(project)));
                        return true;
                    }
                }
                TopLevelItem createProjectFromXML = Jenkins.getInstance().createProjectFromXML(getProjectName(project),
                        inputStream);
                if (createProjectFromXML == null) {
                    messages.add(new JSFeedback(1, "Failed to create " + getProjectName(project)));
                    return false;
                } else {
                    messages.add(new JSFeedback(0, "Successfully created " + getProjectName(project)));
                }
            } catch (CannotResolveClassException e) {
                LogHandler.printStackTrace(this.getClass().getName(), "generateXmlFile", e);
            } catch (IOException e) {
                LogHandler.printStackTrace(this.getClass().getName(), "generateXmlFile", e);
                return false;
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }

        return true;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public FilePath getCategoryPath() {
        return TemplateManager.getInstance().getSvnTemplateFolder().child(category);
    }

    public static AbstractCategory getInstance(String branch, String category, List<String> projects, String version,
            boolean includeHardware, String startTime, String daysToSaveLogs, String branchUrl,
            String nextSystemBuildNumber, LinkedList<JSFeedback> messages, boolean hotfix) throws IOException,
            InterruptedException {
        if (!TemplateManager.getInstance().getSvnTemplateFolder().child(category).exists()) {
            messages.add(new JSFeedback(1, "Invalid configration category"));
            return null;
        }
        /*AbstractCategory abstractCategory = new SystemBuildCategory(branch, category, projects, version,
                includeHardware, startTime, daysToSaveLogs, branchUrl, nextSystemBuildNumber, messages, hotfix);
        if (abstractCategory.isValidConfig()) {

            return abstractCategory;
        }*/

        AbstractCategory abstractCategory = new ProducerConsumerCategory(branch, category, projects, version, includeHardware,
                startTime, daysToSaveLogs, branchUrl, nextSystemBuildNumber, messages, hotfix);
        if (abstractCategory.isValidConfig()) {
            return abstractCategory;
        }

        abstractCategory = new DefaultCategory(branch, category, projects, version, includeHardware, startTime,
                daysToSaveLogs, branchUrl, nextSystemBuildNumber, messages, hotfix);
        if (abstractCategory.isValidConfig()) {
            return abstractCategory;
        }

        return null;
    }

    public boolean isValidConfig() {
        return validConfig;
    }

    public void setValidConfig(boolean validConfig) {
        this.validConfig = validConfig;
    }

    public List<JenkinsProject> getProjects() {
        return projects;
    }

    public void setProjects(List<JenkinsProject> projects) {
        this.projects = projects;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public boolean isIncludeHardware() {
        return includeHardware;
    }

    public void setIncludeHardware(boolean includeHardware) {
        this.includeHardware = includeHardware;
    }

    public HashMap<String, String> getDefeaultParameters() {
        return defeaultParameters;
    }

    public void setDefeaultParameters(HashMap<String, String> defeaultParameters) {
        this.defeaultParameters = defeaultParameters;
    }

    public boolean isHotfix() {
        return hotfix;
    }

    public void setHotfix(boolean hotfix) {
        this.hotfix = hotfix;
    }

    public static class JenkinsProject {
        private String name = "";
        private String xmlTemplate = "";
        private boolean rootProject = false;

        public JenkinsProject(String name, boolean rootProject) {
            this.name = name;
            this.setRootProject(rootProject);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getXmlTemplate() {
            return xmlTemplate;
        }

        public void setXmlTemplate(String xmlTemplate) {
            this.xmlTemplate = xmlTemplate;
        }

        public boolean isRootProject() {
            return rootProject;
        }

        public void setRootProject(boolean rootProject) {
            this.rootProject = rootProject;
        }

    }

}
