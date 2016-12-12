package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.xml.sax.SAXException;

import java.io.IOException;

public abstract class Scm {
    public static Scm create(MavenProject mavenProject, Credentials credentials) {
        if (mavenProject.getScm() == null) {
            throw new IllegalArgumentException("No Scm configured");
        }
        if (mavenProject.getScm().getConnection() == null) {
            throw new IllegalArgumentException("No Scm connection configured");
        }
        if (mavenProject.getScm().getConnection().startsWith("scm:svn:")) {
            return new SubversionScm(credentials);
        }
        throw new IllegalArgumentException("cannot handle scm at " + mavenProject.getScm().getConnection());
    }
    protected final Credentials credentials;

    public Scm(Credentials credentials) {
        this.credentials = credentials;
    }

    public abstract boolean exists(FileNode dir, String url);
    public abstract ScmTag createTag(String tagbase, String tagname, FileNode base, Descriptor descriptor, Log log) throws Failure;
    public abstract String workspaceUrl(FileNode directory) throws Failure;
    public abstract void sparseCheckout(Log log, FileNode result, String url, String revision, boolean tryChanges) throws Failure;

    public abstract Launcher prepareCommit(FileNode checkout, String commitMessage) throws Failure;
    public abstract Launcher prepareDelete(FileNode checkout, String file, String commitMessage) throws Failure;
    public abstract Launcher prepareUnlock(FileNode dir, String file) throws Failure;
    public abstract Launcher prepareLock(FileNode dir, String file) throws Failure;
    public abstract Launcher prepareUpdate(FileNode dir) throws Failure;

    public abstract WorkingCopy createWorkingCopy(FileNode workingCopy) throws IOException, SAXException, XmlException;

    public Credentials credentials() {
        return credentials;
    }



    public static class Credentials {
        public static final Credentials NONE() {
            return new Credentials(null, null);
        }
        private final String username;
        private final String password;
        public Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
        protected String username() {
            return username;
        }
        protected String password() {
            return password;
        }
    }
}
