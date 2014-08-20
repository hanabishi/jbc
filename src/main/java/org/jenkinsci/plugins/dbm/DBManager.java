package org.jenkinsci.plugins.dbm;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Action;
import hudson.model.RootAction;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.View;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.dbm.AbstractCategory.JSFeedback;
import org.jenkinsci.plugins.dbm.TemplateManager.ConfigurationCategory;
import org.jenkinsci.plugins.dbm.TemplateManager.ConfigurationProject;
import org.jenkinsci.plugins.dbm.TemplateManager.ConfigurationTemplate;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import com.thoughtworks.xstream.mapper.CannotResolveClassException;

@Extension
public class DBManager implements RootAction {

    @Override
    public String getDisplayName() {
        return "Jenkins Dashboard Manager";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/dbm/images/DashboardManager.png";
    }

    @Override
    public String getUrlName() {
        return "dbm";
    }

    @SuppressWarnings("rawtypes")
    public void doRemoveHFDashboard(final StaplerRequest request, final StaplerResponse response,
            @QueryParameter String hotfixName) {
        response.setContentType("application/json");
        JSONObject jsonResponse = new JSONObject();
        PrintWriter out = null;
        int code = 0;
        String message = "";
        try {
            if (Security.isAdmin()) {
                for (AbstractProject project : Jenkins.getInstance().getItems(AbstractProject.class)) {
                    if (project.getName().startsWith(hotfixName) && project.getName().contains("-System-")) {
                        try {
                            project.delete();
                            message = message.concat("<span style=\"color: green\">Deleted " + project.getName()
                                    + "</span><br/>");
                        } catch (InterruptedException e) {
                            LogHandler.printStackTrace(this.getClass().getName(), "doRemoveHFDashboard", e);
                            code = 1;
                            message = message.concat("<span style=\"color: red\">Unable to delete " + project.getName()
                                    + "</span><br/>");
                        }
                    }
                }
            } else {
                message = "Access denied";
                code = 1;
            }
            jsonResponse.put("message", message);
            jsonResponse.put("code", new Integer(code));

            out = response.getWriter();
            out.print(jsonResponse.toString(2));
            out.flush();
        } catch (IOException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "doRemoveHFDashboard", e);
        } finally {
            IOUtils.closeQuietly(out);
            jsonResponse.clear();
        }
    }

    public void doCreateHFDashboard(final StaplerRequest request, final StaplerResponse response,
            @QueryParameter String username, @QueryParameter String password, @QueryParameter String hotfixName,
            @QueryParameter String firstBuildNumber, @QueryParameter String svnURL, @QueryParameter String version) {
        response.setContentType("application/json");
        JSONObject jsonResponse = new JSONObject();
        PrintWriter out = null;
        int code = 0;
        String message = "";

        try {
            if (Security.isAdmin()) {
                if (version.isEmpty()) {
                    code = 1;
                    message = "Unable to find the branch " + svnURL;
                } else {
                    HashMap<String, LinkedList<String>> config = new HashMap<String, LinkedList<String>>();
                    config.put("System-Build", new LinkedList<String>());
                    DashboardRequest configuration = new DashboardRequest(svnURL, hotfixName, "30", "01:00",
                            firstBuildNumber, false, version, config, true);
                    try {
                        configuration.createDashboard();
                        message = configuration.getParagraph();
                        code = configuration.getStatus();
                    } catch (InterruptedException e) {
                        LogHandler.printStackTrace(this.getClass().getName(), "doCreateHFDashboard", e);
                        code = 1;
                        message = "Failed to create builds for " + hotfixName;
                    }
                }
            } else {
                message = "Access denied";
                code = 1;
            }
            jsonResponse.put("message", message);
            jsonResponse.put("code", new Integer(code));

            out = response.getWriter();
            out.print(jsonResponse.toString(2));
            out.flush();
        } catch (IOException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "doCreateHFDashboard", e);
        } finally {
            IOUtils.closeQuietly(out);
            jsonResponse.clear();
        }
    }

    public View getRootView() {
        View view = Stapler.getCurrentRequest().findAncestorObject(View.class);
        return view != null ? view : Jenkins.getInstance().getPrimaryView();
    }

    @JavaScriptMethod
    public boolean isAdmin() {
        return (Security.isAdmin());
    }

    public String getViewXML(String branchName) {
        try {
            FilePath view = TemplateManager.getInstance().getSvnTemplateFolder().child("view.xml");
            if (view.exists()) {
                return Matcher.quoteReplacement(view.readToString()).replaceAll(Pattern.quote("[BRANCH]"), branchName);
            }
        } catch (IOException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "getViewXML", e);
        } catch (InterruptedException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "getViewXML", e);
        }
        return "";
    }

    private boolean createView(String branchName) {
        View view = Jenkins.getInstance().getView(branchName);
        if (view == null) {
            InputStream inputStream = null;
            try {
                String xmlData = getViewXML(branchName);
                if (!xmlData.isEmpty()) {
                    inputStream = new ByteArrayInputStream(xmlData.getBytes());
                    View newView = View.createViewFromXML(branchName, inputStream);
                    if (newView != null) {
                        Jenkins.getInstance().addView(newView);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } catch (CannotResolveClassException e) {
                LogHandler.printStackTrace(this.getClass().getName(), "createView", e);
                return false;
            } catch (IOException e) {
                LogHandler.printStackTrace(this.getClass().getName(), "createView", e);
                return false;
            } finally {
                IOUtils.closeQuietly(inputStream);
            }

        }
        return true;
    }

    private boolean removeView(String branchName) {
        View view = Jenkins.getInstance().getView(branchName);
        if (view != null) {
            try {
                Jenkins.getInstance().deleteView(view);
                return true;
            } catch (IOException e) {
                LogHandler.printStackTrace(this.getClass().getName(), "removeView", e);
                return false;
            }
        } else {
            return true;
        }
    }

    public String getRootURL() {
        return Jenkins.getInstance().getRootUrl();
    }

    @JavaScriptMethod
    public JSFeedback updateTemplateFolder() {
        if (!isAdmin()) {
            JSFeedback fb = new JSFeedback();
            fb.code = 1;
            fb.message = "You don't have access to this page";
            return fb;
        }
        JSFeedback fb = new JSFeedback();
        try {
            TemplateManager.getInstance().updateTemplateMap();
            fb.code = 0;
            fb.message = "Updated";
        } catch (IOException e) {
            fb.code = 1;
            fb.message = e.getMessage();
        } catch (InterruptedException e) {
            fb.code = 2;
            fb.message = e.getMessage();
        }

        return fb;

    }

    @JavaScriptMethod
    public JSFeedback createDashboard(String svnurl, String branchName, String days, String startTime,
            String nextSystemBuildNumer, boolean addSystemBuild, boolean includeHardware, String configurationString, String version) {
        if (!isAdmin()) {
            JSFeedback fb = new JSFeedback();
            fb.code = 1;
            fb.message = "You don't have access to this page";
            return fb;
        }

        if (version.isEmpty()) {
            JSFeedback fb = new JSFeedback();
            fb.code = 1;
            fb.message = "Unable to find as version from " + svnurl;
            return fb;
        }
        if (configurationString.isEmpty() && !addSystemBuild) {
            JSFeedback fb = new JSFeedback();
            fb.code = 0;
            fb.message = "No builds selected";
            return fb;
        }

        HashMap<String, LinkedList<String>> config = new HashMap<String, LinkedList<String>>();

        for (String project : configurationString.split(";")) {
            String[] parts = project.split(Pattern.quote("/"));
            if (parts.length != 2) {
                continue;
            }
            if (!config.containsKey(parts[0])) {
                config.put(parts[0], new LinkedList<String>());
            }
            config.get(parts[0]).add(parts[1]);
        }

        JSFeedback fb = new JSFeedback();

        if (createView(branchName)) {

            if (addSystemBuild) {
                config.put("System-Build", new LinkedList<String>());
            }

            DashboardRequest configuration = new DashboardRequest(svnurl, branchName, days, startTime,
                    nextSystemBuildNumer, includeHardware, version, config, false);
            try {
                configuration.createDashboard();
                fb.code = configuration.getStatus();
                fb.message = configuration.getParagraph();
            } catch (IOException e) {
                LogHandler.printStackTrace(this.getClass().getName(), "createDashboard", e);
                fb.code = 1;
                fb.message = e.getMessage();
            } catch (InterruptedException e) {
                LogHandler.printStackTrace(this.getClass().getName(), "createDashboard", e);
                fb.code = 2;
                fb.message = e.getMessage();
            }
        } else {
            fb.code = 3;
            fb.message = "Failed to create view";
        }

        return fb;
    }

    @SuppressWarnings({ "rawtypes" })
    @JavaScriptMethod
    public JSFeedback disableBuilds(String builds) {
        String[] projectList = builds.split(Pattern.quote(";"));
        JSFeedback fb = new JSFeedback();
        fb.code = 0;
        fb.message = "<b>Operations:</b><br/>";
        for (String projectName : projectList) {
            for (AbstractProject project : Jenkins.getInstance().getItems(AbstractProject.class)) {
                if (project.getName().toLowerCase().equalsIgnoreCase(projectName)) {
                    try {
                        project.disable();
                        fb.message = fb.message.concat("<span style=\"color: green\">Disabled " + project.getName()
                                + "</span><br />");
                    } catch (IOException e) {
                        LogHandler.printStackTrace(this.getClass().getName(), "disableBuilds", e);
                        fb.message = fb.message.concat("<span style=\"color: red\">Failed to disable "
                                + project.getName() + "</span><br />");
                        fb.code = 1;
                    }
                }
            }
        }

        return fb;
    }

    @JavaScriptMethod
    public String getConfigurations() {
        String select = "<select id=\"selTemplates\">";
        for (ConfigurationTemplate template : TemplateManager.getInstance().getConfigurationTemplates()) {
            String config = "";
            for (ConfigurationCategory category : template.getCategories()) {
                for (ConfigurationProject project : category.getProjects()) {
                    config = config.concat(category.getName() + "/" + project.getName() + ";");
                }
            }

            select = select.concat("<option systembuild=\"" + template.isIncludeSystemBuild() + "\" value=\"" + config
                    + "\">" + template.getName() + "</option>");
        }
        select = select.concat("</select>");

        return select;
    }

    @SuppressWarnings("rawtypes")
    @JavaScriptMethod
    public JSFeedback deleteBuilds(String builds, String views) {
        String[] projectList = builds.split(Pattern.quote(";"));
        String[] viewsList = views.split(Pattern.quote(";"));
        JSFeedback fb = new JSFeedback();
        fb.message = "<b>Operations:</b><br/>";
        fb.code = 0;

        for (String viewName : viewsList) {
            View v = Jenkins.getInstance().getView(viewName);
            if (v != null) {
                try {
                    Jenkins.getInstance().deleteView(v);
                    fb.message.concat("<span style=\"color: green\">Deleted view " + v.getDisplayName()
                            + "</span><br />");
                } catch (IOException e) {
                    LogHandler.printStackTrace(this.getClass().getName(), "deleteBuilds", e);
                    fb.message = fb.message.concat("<span style=\"color: red\">Failed to delete view "
                            + v.getDisplayName() + "</span><br />");
                }
            }
        }

        for (String projectName : projectList) {
            for (AbstractProject project : Jenkins.getInstance().getItems(AbstractProject.class)) {
                if (project.getName().equalsIgnoreCase(projectName)) {
                    try {
                        project.delete();
                        fb.message.concat("<span style=\"color: green\">Deleted project " + project.getName()
                                + "</span><br />");
                    } catch (IOException e) {
                        LogHandler.printStackTrace(this.getClass().getName(), "deleteBuilds", e);
                        fb.message = fb.message.concat("<span style=\"color: red\">Failed to delete project "
                                + project.getName() + "</span><br />");
                    } catch (InterruptedException e) {
                        LogHandler.printStackTrace(this.getClass().getName(), "deleteBuilds", e);
                        fb.message = fb.message.concat("<span style=\"color: red\">Failed to delete project "
                                + project.getName() + "</span><br />");
                    }
                }
            }
        }

        return fb;
    }

    @SuppressWarnings("rawtypes")
    @JavaScriptMethod
    public JSFeedback startBuilds(String builds) {
        String[] projectList = builds.split(Pattern.quote(";"));
        JSFeedback fb = new JSFeedback();
        fb.message = "<b>Operations:</b><br/>";
        fb.code = 0;
        CauseAction cause = new CauseAction(new Cause.UserIdCause());
        for (String projectName : projectList) {
            for (AbstractProject project : Jenkins.getInstance().getItems(AbstractProject.class)) {
                if (project.getName().equalsIgnoreCase(projectName) && !project.getName().contains("-Consumer-")
                        && !project.getName().contains("System-Integrationtest-")
                        && !project.getName().contains("System-Package") && !project.getName().contains("System-Test")
                        && !project.getName().contains("System-Promote")
                        && !project.getName().contains("System-Release-Management")) {
                    if (!project.isBuilding() && !project.isInQueue()) {
                        Jenkins.getInstance().getQueue().schedule(project, 0, cause);
                        fb.message = fb.message.concat("<span style=\"color: green\">Started " + project.getName()
                                + "</span><br />");
                    }
                }
            }
        }
        return fb;
    }

    /*@SuppressWarnings("rawtypes")
    @JavaScriptMethod
    public JSFeedback cleanBuilds(String builds) {
        String[] projectList = builds.split(Pattern.quote(";"));
        JSFeedback fb = new JSFeedback();
        fb.message = "<b>Operations:</b><br/>";
        fb.code = 0;
        for (String projectName : projectList) {
            for (AbstractProject project : Jenkins.getInstance().getItems(AbstractProject.class)) {
                if (project.getName().equalsIgnoreCase(projectName) && !project.getName().contains("-Consumer-")
                        && !project.getName().contains("-System-") && !project.getName().contains("-Producer-")) {
                    if (project.getScm() instanceof SBOGetmods) {
                        SBOGetmods scm = (SBOGetmods) project.getScm();
                        scm.setForceAdminClean(true);
                        fb.message = fb.message.concat("<span style=\"color: green\">Cleaned " + project.getName()
                                + "</span><br />");
                    } else {
                        fb.message = fb.message.concat("<span style=\"color: red\">Failed to clean "
                                + project.getName() + "</span><br />");
                        fb.code = 1;
                    }
                }
            }
        }
        return fb;
    }*/

    @SuppressWarnings("rawtypes")
    @JavaScriptMethod
    public JSFeedback enableBuilds(String builds) {
        String[] projectList = builds.split(Pattern.quote(";"));
        JSFeedback fb = new JSFeedback();
        fb.code = 0;

        fb.message = "<b>Operations:</b><br/>";
        for (String projectName : projectList) {
            for (AbstractProject project : Jenkins.getInstance().getItems(AbstractProject.class)) {
                if (project.getName().equalsIgnoreCase(projectName)) {
                    try {
                        project.enable();
                        fb.message = fb.message.concat("<span style=\"color: green\">Enabled " + project.getName()
                                + "</span><br />");
                    } catch (IOException e) {
                        LogHandler.printStackTrace(this.getClass().getName(), "disableBuilds", e);
                        fb.message = fb.message.concat("<span style=\"color: red\">Failed to enable "
                                + project.getName() + "</span><br />");
                        fb.code = 1;
                    }
                }
            }
        }
        return fb;
    }

    @SuppressWarnings("rawtypes")
    @JavaScriptMethod
    public String getProjectsDiv(String branch) {
        JSFeedback fb = new JSFeedback();
        fb.code = 0;
        fb.message = "<table width=\"100%\">";
        fb.message = fb.message
                .concat("<tr class=\"tableHeaderProduct\"><td><img src=\""
                        + this.getRootURL()
                        + "/plugin/RepositoryPlugin/images/CollapseAll.png\" border=\"0\" onclick=\"toggleViews(false)\" onmouseover=\"set_mouse_pointer(this)\"/><img src=\""
                        + this.getRootURL()
                        + "/plugin/RepositoryPlugin/images/ExpandAll.png\" border=\"0\" onclick=\"toggleViews(true)\" onmouseover=\"set_mouse_pointer(this)\"/>Existing views for "
                        + branch + "</td></tr>");
        fb.message = fb.message.concat("<tr><td>" + "</td></tr>");

        for (View view : Jenkins.getInstance().getViews()) {
            if (view.getDisplayName().startsWith(branch)) {
                fb.message = fb.message
                        .concat("<tr><td>"
                                + "<input checked=\"true\" type=\"checkbox\" name=\"chkViews\" id=\"chkView"
                                + view.getDisplayName().replaceAll(Pattern.quote(" "), "")
                                        .replaceAll(Pattern.quote("-"), "").replaceAll(Pattern.quote("."), "")
                                + "\" value=\""
                                + view.getDisplayName()
                                + "\">"
                                + "<span onclick=\"toggleCheckbox('chkView"
                                + view.getDisplayName().replaceAll(Pattern.quote(" "), "")
                                        .replaceAll(Pattern.quote("-"), "")
                                + "')\" onmouseover=\"set_mouse_pointer(this)\">" + view.getDisplayName()
                                + "</span></td></tr>");
            }
        }

        fb.message = fb.message
                .concat("<tr class=\"tableHeaderProduct\"><td><img src=\""
                        + this.getRootURL()
                        + "/plugin/RepositoryPlugin/images/CollapseAll.png\" border=\"0\" onclick=\"toggleProjects(false)\" onmouseover=\"set_mouse_pointer(this)\"/><img src=\""
                        + this.getRootURL()
                        + "/plugin/RepositoryPlugin/images/ExpandAll.png\" border=\"0\" onclick=\"toggleProjects(true)\" onmouseover=\"set_mouse_pointer(this)\"/>Existing builds for "
                        + branch + "</td></tr>");
        for (AbstractProject project : Jenkins.getInstance().getItems(AbstractProject.class)) {
            if (project.getName().startsWith(branch + "-")) {
                fb.message = fb.message.concat("<tr><td>"
                        + "<input checked=\"true\" type=\"checkbox\" name=\"chkProjects\" id=\"chkProject"
                        + project.getName().replaceAll(Pattern.quote(" "), "").replaceAll(Pattern.quote("-"), "")
                                .replaceAll(Pattern.quote("."), "") + "\" value=\"" + project.getName() + "\">"
                        + "<span onclick=\"toggleCheckbox('chkProject"
                        + project.getName().replaceAll(Pattern.quote(" "), "").replaceAll(Pattern.quote("-"), "")
                        + "')\" onmouseover=\"set_mouse_pointer(this)\">" + project.getName() + "</span></td></tr>");
            }
        }
        fb.message = fb.message.concat("</table>");
        return fb.message;
    }

    @JavaScriptMethod
    public String getDashboards() {
        String list = "<select onchange=\"updateBranch()\" id=\"selBranchManger\">";
        list = list.concat("<option value=\"\">*** SELECT VIEW ***</option>");
        for (View v : Jenkins.getInstance().getViews()) {
            if (v.getDisplayName().equalsIgnoreCase("all")) {
                continue;
            }
            list = list.concat("<option value=\"" + v.getDisplayName() + "\">" + v.getDisplayName() + "</option>");
        }
        return list.concat("</select>");
    }

    @JavaScriptMethod
    public String getProjectsWithUpdate() {

        try {
            TemplateManager.getInstance().updateTemplateMap();
            return getProjects();
        } catch (IOException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "getProjectsWithUpdate", e);
            return "";
        } catch (InterruptedException e) {
            LogHandler.printStackTrace(this.getClass().getName(), "getProjectsWithUpdate", e);
            return "";
        }
    }

    public String getProjects() {
        int width = 6;
        String tableData = "<table width=\"100%\">";
        int counter = 0;
        HashMap<String, HashMap<String, List<FilePath>>> templates = TemplateManager.getInstance().getTemplates();
        SortedSet<String> keys = new TreeSet<String>(templates.keySet());
        tableData = tableData.concat("<tr><td ><table width=\"100%\"><tr>");
        int tdWidth = ((100 / keys.size()));
        for (String category : keys) {
            tableData = tableData.concat("<td name=\"categoryHeader\" id=\"header"
                    + category.replaceAll(Pattern.quote(" "), "") + "\" onclick=\"toggleSection('"
                    + category.replaceAll(Pattern.quote(" "), "") + "')\" class=\"dbcCategorySelectable\" width=\""
                    + tdWidth + "%\" onmouseover=\"set_mouse_pointer(this)\">" + category + "</td>");
        }
        tableData = tableData.concat("</td></tr></table>");

        tdWidth = ((100 / width));
        for (String category : keys) {
            int i = 0;
            tableData = tableData.concat("<tr><td name=\"categorySection\" id=\""
                    + category.replaceAll(Pattern.quote(" "), "") + "\" style=\"display:none\"><table width=\"100%\">");
            tableData = tableData.concat("<tr>");
            for (Map.Entry<String, List<FilePath>> subCategory : templates.get(category).entrySet()) {
                if (!subCategory.getKey().equals("*")) {
                    tableData = tableData.concat("<tr><td colspan=\"" + width + "\"><b>" + subCategory.getKey()
                            + "</b></td></tr>");
                }
                for (FilePath projectTemplate : subCategory.getValue()) {
                    tableData = tableData
                            .concat("<td width=\""
                                    + tdWidth
                                    + "%\"><input onclick=\"updateBuildsToCreate()\" type=\"checkbox\" name=\"buildSelection\" id=\"chk"
                                    + counter
                                    + "\" value=\""
                                    + category
                                    + "/"
                                    + projectTemplate.getName().replaceAll(Pattern.quote(".xml"), "")
                                    + "\" /><span onclick=\"toggleCheckbox('chk"
                                    + counter
                                    + "')\" onmouseover=\"set_mouse_pointer(this)\">"
                                    + projectTemplate.getName().replaceAll(Pattern.quote("Consumer-"), "")
                                            .replaceAll(Pattern.quote(".xml"), "") + "</span></td>");
                    i++;
                    counter++;
                    if (i > (width - 1)) {
                        i = 0;
                        tableData = tableData.concat("</tr><tr>");
                    }
                }
                if (i > 0) {
                    for (int reduce = (width - i); reduce > 0; reduce--) {
                        tableData = tableData.concat("<td width=\"" + tdWidth + "%\"></td>");
                    }
                }
            }
            tableData = tableData.concat("</tr></table>");
        }
        tableData = tableData.concat("</tr></table></td></tr>");
        return tableData;
    }

    public static class DashboardRequest {

        private LinkedList<JSFeedback> messages = new LinkedList<JSFeedback>();

        private String branch;
        private String branchUrl;
        private String daysToSaveLogs;
        private String startTime;
        private boolean includeHardware;
        private String version;
        private String nextSystemBuildNumber;

        private int status = 0;

        private HashMap<String, LinkedList<String>> config;

        private boolean hotfix;

        public DashboardRequest(String svnurl, String branch, String days, String startTime,
                String nextSystemBuildNumer, boolean includeHardware, String version,
                HashMap<String, LinkedList<String>> config, boolean hotfix) {
            this.nextSystemBuildNumber = nextSystemBuildNumer;
            this.version = version;
            this.branchUrl = svnurl;
            this.branch = branch;
            this.daysToSaveLogs = days;
            this.startTime = startTime;
            this.includeHardware = includeHardware;
            this.config = config;
            this.hotfix = hotfix;
        }

        public void createDashboard() throws IOException, InterruptedException {
            for (Map.Entry<String, LinkedList<String>> entry : config.entrySet()) {
                AbstractCategory categoryConfig = AbstractCategory.getInstance(branch, entry.getKey(),
                        entry.getValue(), version, includeHardware, startTime, daysToSaveLogs, branchUrl,
                        nextSystemBuildNumber, messages, hotfix);
                if (categoryConfig == null || !categoryConfig.createProjects()) {
                    messages.add(new JSFeedback(1, "Failed to process request for " + entry.getKey() + " builds to "
                            + branch));
                    this.setStatus(1);
                } else {
                    messages.add(new JSFeedback(0, "Successfully created builds for " + entry.getKey()));
                }
            }
        }

        public String getParagraph() {
            String data = "<b>Build result</b><br />";
            for (JSFeedback message : messages) {
                data = data.concat("<span style=\"color: " + ((message.code == 0) ? "green" : "red") + "\">"
                        + message.message + "</span><br />");
            }
            return data;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }
    }
}
