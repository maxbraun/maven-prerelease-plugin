package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.xml.Selector;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.plugin.logging.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Subversion extends Scm {
    private static final Pattern PATTERN = Pattern.compile("^URL:(.*)$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public Subversion(Credentials credentials) {
        super(credentials);
    }

    private Launcher launcher(FileNode dir, String... args) {

        Launcher launcher = new Launcher(dir, "svn", "--non-interactive", "--no-auth-cache");
        if (credentials != null && credentials.username() != null) {
            launcher = launcher.arg("--username", credentials.username());
        }
        if (credentials != null && credentials.password() != null) {
            launcher = launcher.arg("--password", credentials.password());
        }
        return launcher.arg(args);
    }

    @Override
    public Launcher prepareCommit(FileNode checkout, String commitMessage) throws Failure {
        return launcher(checkout, "commit", "-m", commitMessage);

    }

    @Override
    public Launcher prepareDelete(FileNode checkout, String file, String commitMessage) throws Failure{
        return launcher(checkout, "delete", "-m", commitMessage);
    }


    @Override
    public boolean exists(FileNode dir, String url) {
        try {
            launcher(dir, "ls", url).exec();
            return true;
        } catch (Failure e) {
            return false;
        }
    }

    @Override
    public void sparseCheckout(Log log, FileNode result, String url, String revision, boolean tryChanges) throws Failure {
        log.debug(launcher(result.getParent(), "co", "-r", revision, "--depth", "empty", url, result.getName()).exec());
        log.debug(launcher(result, "up", "-r", revision, "pom.xml").exec());
        if (tryChanges) {
            log.debug(launcher(result, "up", "-r", revision, "--depth", "empty", "src/changes").exec());
            log.debug(launcher(result, "up", "-r", revision, "--depth", "empty", "src").exec());
            log.debug(launcher(result, "up", "-r", revision, "src/changes/changes.xml").exec());
        }
    }
    @Override
    public Launcher prepareUnlock(FileNode dir, String file) throws Failure {
        return launcher(dir, "unlock" , file);
    }

    @Override
    public Launcher prepareLock(FileNode dir, String file) throws Failure {
        return launcher(dir, "lock" , file);
    }

    @Override
    public Launcher prepareUpdate(FileNode dir) throws Failure {
        return launcher(dir, "up");
    }

    /** TODO: memory consumption */
    @Override
    public WorkingCopy createWorkingCopy(FileNode workingCopy) throws IOException, SAXException, XmlException {
        World world;
        String output;
        Document doc;
        String path;
        Selector selector;
        Element wcStatus;
        List<FileNode> modifications;
        SortedSet<Long> revisions;
        SortedSet<Long> changes;
        // maps paths to the change revision
        Map<String, Long> maybePendings;
        List<String> pendings;
        long revision;
        long change;
        String props;

        output = launcher(workingCopy, "--xml", "-v", "--show-updates", "status").exec();
        world = workingCopy.getWorld();
        doc = world.getXml().getBuilder().parseString(output);
        selector = world.getXml().getSelector();
        modifications = new ArrayList<>();
        revisions = new TreeSet<>();
        changes = new TreeSet<>();
        maybePendings = new HashMap<>();
        for (Element entry : selector.elements(doc, "status/target/entry")) {
            path = entry.getAttribute("path");
            wcStatus = selector.element(entry, "wc-status");
            props = wcStatus.getAttribute("props");
            if ("normal".equals(wcStatus.getAttribute("item")) && ("none".equals(props) || "normal".equals(props))) {
                revision = Long.parseLong(wcStatus.getAttribute("revision"));
                revisions.add(revision);
                change = Long.parseLong(selector.element(wcStatus, "commit").getAttribute("revision"));
                changes.add(change);
                if (selector.elementOpt(entry, "repos-status") != null) {
                    maybePendings.put(entry.getAttribute("path"), change);
                }
            } else {
                modifications.add(workingCopy.join(path));
            }
        }
        if (changes.size() == 0) {
            throw new IOException("Cannot determine svn status - is this directory under svn?");
        }
        if (revisions.size() == 0) {
            throw new IllegalStateException();
        }
        if (changes.last() > revisions.last()) {
            throw new IllegalStateException(changes.last() + " vs " + revisions.last());
        }
        pendings = new ArrayList<>();
        for (Map.Entry<String, Long> entry : maybePendings.entrySet()) {
            if (entry.getValue() < revisions.last()) {
                output = launcher(workingCopy,"log", "--xml", "-q", "-r" + (entry.getValue() + 1) + ":" + revisions.last(),
                        entry.getKey()).exec();
                doc = world.getXml().getBuilder().parseString(output);
                if (selector.elements(doc, "log/logentry").size() > 0) {
                    pendings.add(entry.getKey());
                }
            }
        }
        return new WorkingCopy(workingCopy, modifications, revisions, changes, pendings, this);
    }

    @Override
    public ScmTag createTag(String tagbase, String tagname, FileNode base, Descriptor descriptor, Log log) throws Failure {
        FileNode tags = base.join("tags");
        FileNode checkout = tags.join(tagname);
        log.debug(launcher(base,"checkout", "--depth=empty", tagbase, tags.getAbsolute()).exec());
        log.debug(launcher(base, "copy", "-r" + descriptor.revision, descriptor.svnOrig, checkout.getAbsolute()).exec());
        return new ScmTag(base, checkout, tagname);
    }


    @Override
    public String workspaceUrl(FileNode directory) throws Failure {
        String str;
        Matcher matcher;

        str = launcher(directory, "info").exec();
        matcher = PATTERN.matcher(str);
        if (!matcher.find()) {
            throw new IllegalStateException("cannot determine checkout url in " + str);
        }
        return matcher.group(1).trim();
    }

}
