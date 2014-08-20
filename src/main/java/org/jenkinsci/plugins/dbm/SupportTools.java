//package org.jenkinsci.plugins.dbm;
//
//import hudson.FilePath;
//import hudson.Launcher;
//import hudson.model.TaskListener;
//import hudson.model.AbstractBuild;
//import hudson.model.AbstractProject;
//import hudson.model.Cause;
//import hudson.model.Node;
//import hudson.model.Run;
//import hudson.model.User;
//import hudson.util.FormValidation;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.FileReader;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.io.Reader;
//import java.net.MalformedURLException;
//import java.net.URL;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.util.HashMap;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.zip.ZipFile;
//
//import jenkins.model.Jenkins;
//
//import org.apache.commons.io.IOUtils;
//import org.dom4j.DocumentException;
//import org.dom4j.Element;
//import org.dom4j.io.SAXReader;
//
//public class SupportTools {
//
//    public static String PARSER_ERROR = "<parse_error>";
//
//    public static final HashMap<Character, String> translation = new HashMap<Character, String>() {
//        private static final long serialVersionUID = 1L;
//
//        {
//            put('%', "%%");
//            put('^', "^^");
//            put('&', "^&");
//            put('<', "^<");
//            put('>', "^>");
//            put('|', "^|");
//        }
//    };
//
//    public static String translatePasswordBatch(String password) {
//        String translatedPassword = "";
//        for (char c : password.toCharArray()) {
//            translatedPassword += (translation.containsKey(c)) ? translation.get(c) : c;
//        }
//        return translatedPassword;
//    }
//
//    public static String getEmail(String userid, TaskListener listener) {
//        String email = "";
//        try {
//            URL apiURL = new URL(Jenkins.getInstance().getRootUrl() + "/securityRealm/user/" + userid + "/api/xml");
//            @SuppressWarnings("unchecked")
//            List<Element> elements = new SAXReader().read(apiURL).getRootElement().elements("property");
//            for (Element elem : elements) {
//                Element add = elem.element("address");
//                if (add != null) {
//                    email = add.getText();
//                    break;
//                }
//            }
//        } catch (MalformedURLException e) {
//            email = "<INVALID URL>";
//            LogHandler.printStackTrace(listener, "SupportTools", "getEmail (" + userid + ")", e);
//        } catch (DocumentException e) {
//            email = "<PARSE ERROR>";
//            LogHandler.printStackTrace(listener, "SupportTools", "getEmail (" + userid + ")", e);
//        }
//
//        return email;
//    }
//
//    public static String getEmail(User user, TaskListener listener) {
//        if (user == null) {
//            return "";
//        }
//
//        return getEmail(user.getId(), listener);
//    }
//
//    public static int tryParse(String value) {
//        int numericValue = 0;
//        try {
//            numericValue = Integer.parseInt(value);
//        } catch (NumberFormatException e) {
//            numericValue = 0;
//        }
//        return numericValue;
//    }
//
//    public static long tryParseLong(String value) {
//        long numericValue = 0;
//        try {
//            numericValue = Long.parseLong(value);
//        } catch (NumberFormatException e) {
//            numericValue = 0;
//        }
//        return numericValue;
//    }
//
//    public static String getXmlText(String line) {
//        if (line.startsWith("<nspbuild")) {
//            int startPos = line.indexOf("<![CDATA[") + 9;
//            int endPos = line.indexOf("]]>");
//            if (endPos < startPos) {
//                endPos = line.length();
//            }
//            if (startPos > 9 && endPos > 0 && endPos < line.length() && startPos < line.length()) {
//                return line.substring(startPos, endPos);
//            }
//        } else {
//            return line.replace(Pattern.quote("</nspbuildlog>"), "").replace(Pattern.quote("]]>"), "");
//        }
//        return "";
//    }
//
//    public static AbstractProject<?, ?> getMyProject(AbstractBuild<?, ?> build) {
//        return build.getRootBuild().getProject();
//    }
//
//    public static String formatDuration(int s) {
//        int hours = s / 3600, remainder = s % 3600, minutes = remainder / 60, seconds = remainder % 60;
//
//        return ((hours < 10 ? "0" : "") + hours + ":" + (minutes < 10 ? "0" : "") + minutes + ":"
//                + (seconds < 10 ? "0" : "") + seconds);
//    }
//
//    public static boolean checkNoFailedUpstream(AbstractBuild<?, ?> build) {
//        boolean downStreamResult = true;
//        AbstractProject<?, ?> project = SupportTools.getMyProject(build);
//        if (project == null) {
//            return true;
//        }
//        for (AbstractProject<?, ?> upstreamProject : project.getUpstreamProjects()) {
//            downStreamResult &= noFailedUpstream(upstreamProject);
//        }
//        return downStreamResult;
//
//    }
//
//    public static boolean noFailedUpstream(AbstractProject<?, ?> project) {
//        if (project == null) {
//            return true;
//        }
//        boolean downStreamResult = project.getLastBuild().equals(project.getLastSuccessfulBuild());
//        // project.getLastBuild().getNumber()
//        for (AbstractProject<?, ?> upstreamProject : project.getUpstreamProjects()) {
//            downStreamResult &= noFailedUpstream(upstreamProject);
//        }
//
//        return downStreamResult;
//    }
//
//    private static Node getNode(String nodeLabel) {
//        nodeLabel = nodeLabel.replaceAll("\\\"", "");
//        for (Node node : Jenkins.getInstance().getNodes()) {
//            String name = node.getDisplayName().replaceAll("\\\"", "");
//            String[] labels = node.getLabelString().split(Pattern.quote(" "));
//
//            if (name.equalsIgnoreCase(nodeLabel)) {
//                return node;
//            }
//            for (String labelName : labels) {
//                if (labelName.equalsIgnoreCase(nodeLabel)) {
//                    return node;
//                }
//            }
//        }
//        return null;
//    }
//
//    public static void storeServerName(String name, File root, boolean isUnix, TaskListener listener) {
//        FileOutputStream stream = null;
//        PrintWriter writer = null;
//        try {
//            File serverFile = root;
//            if (!root.isFile()) {
//                serverFile = new File(root, "windows.server");
//                if (isUnix) {
//                    serverFile = new File(root, "linux.server");
//                }
//            }
//            e
//            LogHandler.printInfo(listener, "Logging " + name + " in " + serverFile);
//            stream = new FileOutputStream(serverFile);
//            writer = new PrintWriter(stream);
//            writer.println(name);
//            writer.flush();
//        } catch (FileNotFoundException e) {
//            LogHandler.printStackTrace(listener, "BuildConfiguration", "saveCurrentSecondaryServerName", e);
//        } finally {
//            IOUtils.closeQuietly(stream);
//            IOUtils.closeQuietly(writer);
//        }
//    }
//
//    public static String getServerName(boolean isUnix, File root, TaskListener listener) {
//        String server = "";
//        FileReader fileReader = null;
//        BufferedReader reader = null;
//
//        try {
//            File serverFile = root;
//            if (!root.isFile()) {
//                serverFile = new File(root, "windows.server");
//                if (isUnix) {
//                    serverFile = new File(root, "linux.server");
//                }
//            }
//
//            fileReader = new FileReader(serverFile);
//            reader = new BufferedReader(fileReader);
//            server = reader.readLine();
//            if (server != null) {
//                server = server.trim();
//            }
//        } catch (FileNotFoundException e) {} catch (IOException e) {
//            LogHandler.printStackTrace(listener, "SBOGetmods", "getRevision", e);
//        } finally {
//            IOUtils.closeQuietly(fileReader);
//            IOUtils.closeQuietly(reader);
//        }
//        if (server == null) {
//            return "";
//        }
//        return server;
//    }
//
//    public static void updateChildBuilds(AbstractProject<?, ?> project, Node node, TaskListener listener, boolean isUnix)
//            throws Exception {
//        for (AbstractProject<?, ?> downstreamProject : project.getDownstreamProjects()) {
//            SupportTools.updateChildBuilds(downstreamProject, node, listener, isUnix);
//            boolean blockUp = downstreamProject.blockBuildWhenUpstreamBuilding();
//            boolean blockDown = downstreamProject.blockBuildWhenDownstreamBuilding();
//            String displayName = downstreamProject.getDisplayName().toLowerCase();
//            if ((!blockUp && !blockDown) || displayName.contains("integrationtest")
//                    || displayName.contains("release-management")) {
//                continue;
//            }
//
//            try {
//                String assignedLabel = downstreamProject.getAssignedLabelString();
//                if (assignedLabel == null) {
//                    assignedLabel = "windows";
//                }
//
//                Node currentNode = SupportTools.getNode(assignedLabel);
//                if (currentNode != null) {
//                    boolean useUnix = currentNode.getLabelString().toLowerCase().contains("linux");
//                    if (useUnix == isUnix) {
//                        downstreamProject.setAssignedNode(node);
//                    }
//                }
//                SupportTools.storeServerName(node.getNodeName(), downstreamProject.getRootDir(), isUnix, listener);
//            } catch (IOException e) {
//                LogHandler.printStackTrace(listener,
//                        downstreamProject.getDisplayName() + " SupportTools (IOException)", "updateChildBuilds", e);
//            } catch (Exception e) {
//                LogHandler.printStackTrace(listener, downstreamProject.getDisplayName() + " SupportTools (Exception)",
//                        "updateChildBuilds", e);
//            }
//        }
//    }
//
//    public static void simpleWriter(final File file, final String data) {
//        FileWriter fileWriter = null;
//        try {
//            fileWriter = new FileWriter(file);
//            fileWriter.write(data);
//            fileWriter.flush();
//        } catch (IOException e) {
//            LogHandler.printStackTrace(TaskListener.NULL, "SupportTools", "simpleWriter", e);
//        } finally {
//            IOUtils.closeQuietly(fileWriter);
//        }
//    }
//
//    public static AbstractProject<?, ?> getParentUpstreamProjects(AbstractBuild<?, ?> build) {
//        AbstractProject<?, ?> firstProject = build.getProject();
//        if (!firstProject.getParent().getDisplayName().equalsIgnoreCase("jenkins")) {
//            firstProject = firstProject.getRootProject();
//        }
//
//        boolean buildFound = false;
//        while (!buildFound) {
//            @SuppressWarnings("rawtypes")
//            List<AbstractProject> prevStep = firstProject.getUpstreamProjects();
//
//            if (prevStep.size() > 0) {
//                firstProject = prevStep.get(0);
//            } else {
//                buildFound = true;
//            }
//        }
//        return firstProject;
//    }
//
//    public static Run<?, ?> getParentBuild(AbstractBuild<?, ?> build) {
//        return getParentUpstreamProjects(build).getLastBuild();
//    }
//
//    public static Run<?, ?> getParentSuccessfullBuild(AbstractBuild<?, ?> build) {
//        // We need to rewrite this code to support the issues with getting the
//        // wrong builds
//        // When running Producer-Consumer, until we find a good solution we will
//        // try using
//        // a sleep to work around the issue :(
//        AbstractProject<?, ?> parentUpstreamProjects = getParentUpstreamProjects(build);
//        try {
//            // QFE to see if this will reduce the risk of using the wrong build
//            // when running Producer-Consumer.
//            Thread.sleep(20000);
//        } catch (InterruptedException e) {}
//        String apa = "";
//        for (Cause c : build.getCauses()) {
//            String nisse = c.getShortDescription();
//            apa = apa.concat(nisse + "\n");
//        }
//        return getParentUpstreamProjects(build).getLastSuccessfulBuild();
//    }
//
//    public static HashMap<String, String> splitDeviceName(String hostName, TaskListener listener) {
//        HashMap<String, String> result = new HashMap<String, String>();
//        String[] device = hostName.split("\\(");
//        if (device.length < 1) {
//            result.put("ip", "0.0.0.0");
//            result.put("dns", "");
//        } else if (device.length == 1) {
//            result.put("ip", "0.0.0.0");
//            result.put("dns", hostName);
//        } else {
//            if (device[1].contains(("."))) {
//                result.put("ip", device[1].replaceAll("\\(", "").replaceAll("\\)", "").trim());
//                result.put("dns", device[0].trim());
//            } else {
//                result.put("ip", device[0].replaceAll("\\(", "").replaceAll("\\)", "").trim());
//                result.put("dns", device[1].trim());
//            }
//        }
//        return result;
//
//    }
//
//    public static boolean isNullOrEmpty(String value) {
//        return (value == null || value.trim().isEmpty());
//    }
//
//    public static void tryClose(ZipFile inputStream) {
//        try {
//            if (inputStream != null) {
//                inputStream.close();
//            }
//        } catch (IOException e) {
//            // do nothing
//        }
//    }
//
//    public static void tryClose(java.sql.Connection con) {
//        try {
//            if (con != null) {
//                con.close();
//            }
//        } catch (SQLException e) {
//            // do nothing
//        }
//    }
//
//    public static void tryClose(Reader reader) {
//        try {
//            if (reader != null) {
//                reader.close();
//            }
//        } catch (IOException e) {
//            // do nothing
//        }
//    }
//
//    public static void tryClose(PreparedStatement stream) {
//        try {
//            if (stream != null) {
//                stream.close();
//            }
//        } catch (SQLException e) {
//            // do nothing
//        }
//    }
//
//    public static void tryClose(ResultSet stream) {
//        try {
//            if (stream != null) {
//                stream.close();
//            }
//        } catch (SQLException e) {
//            // do nothing
//        }
//    }
//
//    public static FormValidation validNumber(String value, int max, int min) {
//        try {
//            SupportTools.validateNumber(value, max, min);
//            return FormValidation.ok();
//        } catch (NumberFormatException e) {
//            return FormValidation.error(e.getMessage());
//        }
//    }
//
//    public static boolean validateNumber(String value, int max, int min) throws NumberFormatException {
//        int val = Integer.parseInt(value);
//        if (value.equals(val + "")) {
//            if (val < min || val > max) {
//                throw new NumberFormatException("The value needs to be a number between " + min + " and " + max);
//            } else {
//                return true;
//            }
//        } else {
//            throw new NumberFormatException("The value needs to be a valid number");
//        }
//    }
//
//    public static class JSFeedback {
//        public int code = 0;
//        public String message = "";
//
//        public JSFeedback() {
//        }
//
//        public JSFeedback(int code, String message) {
//            this.code = code;
//            this.message = message;
//        }
//    }
//
//}
