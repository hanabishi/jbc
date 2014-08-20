package org.jenkinsci.plugins.dbm;

import hudson.model.User;

import java.util.Arrays;
import java.util.LinkedList;

public class Security {

    public static LinkedList<String> admins = new LinkedList<String>(Arrays.asList("marcusja"));

    public static boolean isAdmin() {
        User user = User.current();
        return (user != null && admins.contains(user.getId().toLowerCase()));
    }
}
