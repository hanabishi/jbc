package org.jenkinsci.plugins.jbm;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.Digester2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import jenkins.model.Jenkins;

import org.apache.commons.digester.Digester;
import org.apache.commons.io.IOUtils;

public class TemplateManager {

    public static TemplateManager instance = null;

    private FilePath svnTemplateFolder = null;
    private HashMap<String, HashMap<String, List<FilePath>>> templates = new HashMap<String, HashMap<String, List<FilePath>>>();
    private List<ConfigurationTemplate> configurationTemplates = new LinkedList<ConfigurationTemplate>();
    private boolean failedToUpdateTemplates = false;

    public static TemplateManager getInstance() {
        if (instance == null) {
            instance = new TemplateManager();
        }
        return instance;
    }

    private TemplateManager() {
    }

    public void updateConfigurationTemplateMap() throws IOException, InterruptedException {
        InputStream inputStream = null;
        Reader reader = null;
        try {
            FilePath templateFile = this.getSvnTemplateFolder().child("templates.xml");
            configurationTemplates.clear();
            if (!templateFile.exists()) {
                this.setFailedToUpdateTemplates(true);
            }
            Digester digester = new Digester2();
            digester.push(configurationTemplates);

            digester.addObjectCreate("*/template", TemplateManager.ConfigurationTemplate.class);
            digester.addSetProperties("*/template", "name", "name");
            digester.addSetProperties("*/template", "includeSystemBuild", "includeSystemBuild");
            digester.addSetNext("*/template", "add");

            digester.addObjectCreate("*/category", ConfigurationCategory.class);
            digester.addSetProperties("*/category", "name", "name");
            digester.addSetNext("*/category", "addCategory");

            digester.addObjectCreate("*/project", ConfigurationProject.class);
            digester.addSetProperties("*/project", "name", "name");
            digester.addSetNext("*/project", "addProject");

            inputStream = new ByteArrayInputStream(templateFile.readToString().getBytes());
            reader = new InputStreamReader(inputStream, "UTF-8");
            digester.parse(reader);
        } catch (IOException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "updateConfigurationTemplateMap", e);
            this.setFailedToUpdateTemplates(true);
        } catch (Exception e) {
            LogHandler.printStackTrace(this.getClass().getName(), "updateConfigurationTemplateMap", e);
            this.setFailedToUpdateTemplates(true);
        } finally {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(inputStream);
        }
    }

    public static class ConfigurationTemplate {
        private List<ConfigurationCategory> categories = new LinkedList<ConfigurationCategory>();
        private String name;
        private boolean includeSystemBuild;

        public void addCategory(ConfigurationCategory category) {
            getCategories().add(category);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isIncludeSystemBuild() {
            return includeSystemBuild;
        }

        public void setIncludeSystemBuild(boolean includeSystemBuild) {
            this.includeSystemBuild = includeSystemBuild;
        }

        public List<ConfigurationCategory> getCategories() {
            return categories;
        }

        public void setCategories(List<ConfigurationCategory> categories) {
            this.categories = categories;
        }

    }

    public static class ConfigurationCategory {
        private List<ConfigurationProject> projects = new LinkedList<ConfigurationProject>();
        private String name;

        public void addProject(ConfigurationProject project) {
            projects.add(project);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ConfigurationProject> getProjects() {
            return projects;
        }

        public void setProjects(List<ConfigurationProject> projects) {
            this.projects = projects;
        }

    }

    public static class ConfigurationProject {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public void updateTemplateMap() throws IOException, InterruptedException {
        templates.clear();
        for (FilePath path : getSvnTemplateFolder().list()) {
            if (path.isDirectory()) {
                if (path.getName().equals(".svn")) {
                    continue;
                }
                HashMap<String, List<FilePath>> config = new HashMap<String, List<FilePath>>();
                if (path.child("Producer.xml").exists()) {
                    for (FilePath subpath : path.list()) {
                        if (subpath.isDirectory()) {
                            if (subpath.getName().equals(".svn")) {
                                continue;
                            }
                            templates.put(path.getName(), config);
                            if (path.isDirectory()) {
                                List<FilePath> temp = new LinkedList<FilePath>();
                                for (FilePath subsubpath : subpath.list()) {
                                    if (!subsubpath.isDirectory()) {
                                        temp.add(subsubpath);
                                    }
                                }
                                config.put(subpath.getName(), temp);
                            }
                        }
                    }
                } else if (path.getName().equalsIgnoreCase("system-build")) {} else {
                    templates.put(path.getName(), config);
                    config.put("*", path.list());
                }
            }
        }

    }

    public boolean isFailedToUpdateTemplates() {
        return failedToUpdateTemplates;
    }

    public void setFailedToUpdateTemplates(boolean failedToUpdateTemplates) {
        this.failedToUpdateTemplates = failedToUpdateTemplates;
    }

    public FilePath getSvnTemplateFolder() {
        return svnTemplateFolder;
    }

    public void setSvnTemplateFolder(FilePath svnTemplateFolder) {
        this.svnTemplateFolder = svnTemplateFolder;
    }

    public HashMap<String, HashMap<String, List<FilePath>>> getTemplates() {
        return templates;
    }

    public void setTemplates(HashMap<String, HashMap<String, List<FilePath>>> templates) {
        this.templates = templates;
    }

    public List<ConfigurationTemplate> getConfigurationTemplates() {
        return configurationTemplates;
    }

    public void setConfigurationTemplates(List<ConfigurationTemplate> configurationTemplates) {
        this.configurationTemplates = configurationTemplates;
    }
}
