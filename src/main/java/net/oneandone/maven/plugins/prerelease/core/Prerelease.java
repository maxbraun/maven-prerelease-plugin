/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.plugins.prerelease.core;

import net.oneandone.maven.plugins.prerelease.util.ChangesXml;
import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.maven.plugins.prerelease.util.PrepareExecutionListener;
import net.oneandone.maven.plugins.prerelease.util.Subversion;
import net.oneandone.maven.plugins.prerelease.util.Transform;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.GroupRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.MetadataBridge;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.deployment.DeploymentException;
import org.sonatype.aether.repository.RemoteRepository;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Prerelease {
    public static Prerelease load(Target target) throws IOException {
        Descriptor descriptor;
        FileNode workingCopy;

        descriptor = Descriptor.load(target);
        workingCopy = target.join("tags", descriptor.getTagName());
        workingCopy.checkDirectory();
        return new Prerelease(target, workingCopy, descriptor);
    }

    public static Prerelease create(Maven maven, Map<String, String> propertyArgs, Log log, Descriptor descriptor, Target target) throws Exception {
        Prerelease prerelease;
        FileNode tags;
        FileNode checkout;
        String tagname;
        String tagbase;
        int idx;

        log.info("creating un-committed tag ...");
        if (descriptor.svnTag.endsWith("/")) {
            throw new IllegalArgumentException(descriptor.svnTag);
        }
        idx = descriptor.svnTag.lastIndexOf('/');
        if (idx == -1) {
            throw new IllegalArgumentException(descriptor.svnTag);
        }
        tagbase = descriptor.svnTag.substring(0, idx);
        tagname = descriptor.svnTag.substring(idx + 1);
        target.create();
        try {
            tags = target.join("tags");
            checkout = tags.join(tagname);
            log.debug(target.svnLauncher("checkout", "--depth=empty", tagbase, tags.getAbsolute()).exec());
            log.debug(target.svnLauncher("copy", "-r" + descriptor.revision, descriptor.svnOrig, checkout.getAbsolute()).exec());
            prerelease = new Prerelease(target, checkout, descriptor);
            prerelease.descriptor.save(target);
            Transform.adjustPom(prerelease.checkout.join("pom.xml"), descriptor.previous, descriptor.project.version,
                    descriptor.svnOrig, descriptor.svnTag);
            Archive.adjustChangesOpt(prerelease.checkout, prerelease.descriptor.project.version);
            prerelease.create(maven, propertyArgs);
            log.info("created prerelease in " + prerelease.target);
        } catch (Exception e) {
            target.scheduleRemove(log, "create failed: " + e.getMessage());
            throw e;
        }
        return prerelease;
    }

    //--

    /** base directory with all data for this prerelease. Subdirectory of the archive directory. */
    public final Target target;
    public final FileNode checkout;
    public final Descriptor descriptor;

    public Prerelease(Target target, FileNode checkout, Descriptor descriptor) throws IOException {
        this.target = target;
        this.checkout = checkout;
        this.descriptor = descriptor;
    }

    public FileNode artifacts() {
        return target.join("artifacts");
    }

    public FileNode frischfleisch() {
        return target.join("frischfleisch.properties");
    }

    /**
     * Deploy by invoking deploy:deploy-file. I don't use the ArtifactDeployer because
     * a) it only one artifact at a time
     * b) deploy-file is more transparent
     */
    public void deploySnapshot(Maven maven, Map<String, String> userProperties, MavenProject originalProject) throws Exception {
        DeploymentRepository dest;
        Map<FileNode, String[]> artifacts;
        FileNode pom;
        FileNode main;
        String[] args;

        artifacts = artifactFiles();
        removeNullClassifier(artifacts, "pom");
        main = removeNullClassifier(artifacts, null);
        pom = checkout.getWorld().file(originalProject.getFile().getAbsolutePath());
        if (main == null) {
            main = pom;
        }
        dest = originalProject.getDistributionManagement().getSnapshotRepository();
        args = new String[] { "org.apache.maven.plugins:maven-deploy-plugin:deploy-file",
                "-Durl=" + dest.getUrl(), "-DrepositoryId=" + dest.getId(), "-DgeneratePom=false", "-DuniqueVersion=true",
                "-Dfile=" + main.getAbsolute(),
                "-DpomFile=" + pom.getAbsolute() };
        if (!artifacts.isEmpty()) {
            args = Strings.append(args, sideArtifacts(artifacts));
        }
        maven.build(checkout, userProperties, args);
    }

    private static String[] sideArtifacts(Map<FileNode, String[]> artifacts) {
        boolean first;
        StringBuilder files;
        StringBuilder classifiers;
        StringBuilder types;
        String[] tmp;

        first = true;
        files = new StringBuilder("-Dfiles=");
        classifiers = new StringBuilder("-Dclassifiers=");
        types = new StringBuilder("-Dtypes=");
        for (Map.Entry<FileNode, String[]> entry : artifacts.entrySet()) {
            if (first) {
                first = false;
            } else {
                files.append(',');
                classifiers.append(',');
                types.append(',');
            }
            tmp = entry.getValue();
            files.append(entry.getKey().getAbsolute());
            classifiers.append(tmp[0]);
            types.append(tmp[1]);
        }
        return new String[] { files.toString(), classifiers.toString(), types.toString() };
    }

    private static FileNode removeNullClassifier(Map<FileNode, String[]> artifacts, String type) {
        Iterator<Map.Entry<FileNode, String[]>> iter;
        String[] tmp;
        Map.Entry<FileNode, String[]> entry;

        iter = artifacts.entrySet().iterator();
        while (iter.hasNext()) {
            entry = iter.next();
            tmp = entry.getValue();
            if (tmp[0] == null) {
                if (type == null || type.equals(tmp[1])) {
                    iter.remove();
                    return entry.getKey();
                }
            }
        }
        return null;
    }


    public void commit(Log log, String commitMessage) throws Failure {

        Launcher launcher;

        log.info("committing tag:");
        launcher = Subversion.launcher(checkout, "commit", "-m", commitMessage);
        log.info(launcher.toString());
        log.info(launcher.exec());
    }

    public void revertCommit(Log log, String by) throws Failure {
        Launcher launcher;

        launcher = Subversion.launcher(checkout, "delete", "-m reverted promotion of prerelease " + descriptor.revision
                + " promoted by " + by, descriptor.svnTag);
        log.info(launcher.toString());
        log.info(launcher.exec());
    }

    public FileNode prepareOrigCommit(Log log) throws IOException, XmlException, SAXException, MojoExecutionException {
        FileNode result;
        ChangesXml changes;

        result = checkout.getWorld().getTemp().createTempDirectory();
        Subversion.sparseCheckout(log, result, descriptor.svnOrig, "HEAD", true);
        try {
            changes = ChangesXml.load(result);
        } catch (FileNotFoundException e) {
            log.info("no changes.xml to adjust.");
            changes = null;
        }

        Subversion.launcher(result, "lock", "pom.xml");
        if (changes != null) {
            Subversion.launcher(result, "lock", ChangesXml.PATH);
        }

        // make sure the version we've locked is what we will modify:
        // (or in other words: make sure we see possible changes that were committed between checkout and lock)
        Subversion.launcher(result, "up");

        Transform.adjustPom(result.join("pom.xml"), descriptor.previous, descriptor.next, null, null);
        if (changes != null) {
            changes.releaseDate(descriptor.project.version, new Date());
            changes.save();
        }
        // svn up does not fail for none-existing files!
        return result;
    }

    //--

    public void create(Maven maven, Map<String, String> propertyArgs) throws Exception {
        FileNode installed;

        // no "clean" because we have a vanilla directory from svn
        try {
            maven.build(checkout, maven.releaseProps(propertyArgs), new PrepareExecutionListener(this, maven.getExecutionListener()), false, "install");
        } finally {
            installed = descriptor.project.localRepo(maven);
            if (installed.exists()) {
                installed.move(artifacts());
            }
        }
        // TODO: check that the workspace is without modifications
    }

    public void verify(Maven maven, String profile, Map<String, String> propertyArgs) throws Exception {
        maven.build(checkout, propertyArgs, "verify", /* to save disk space: */ "clean", "-P" + profile);
    }

    //-- promote

    public void promote(Log log, Map<String, String> propertyArgs, String createTagMessage, String revertTagMessage, String nextIterationMessage, Maven maven) throws Exception {
        FileNode origCommit;

        log.info("promoting revision " + descriptor.revision + " to " + descriptor.project);
        origCommit = prepareOrigCommit(log);
        try {
            promoteLocked(log, propertyArgs, createTagMessage, revertTagMessage, nextIterationMessage, origCommit, maven);
        } catch (Throwable e) { // CAUTION: catching exceptions is not enough -- in particular, out-of-memory during upload is an error!
            try {
                origUnlock(origCommit);
            } catch (Exception nested) {
                e.addSuppressed(nested);
            }
            throw e;
        }
        origUnlock(origCommit);
        log.info("SUCCESS: released " + descriptor.project);
    }

    private void origUnlock(FileNode origCommit) {
        Subversion.launcher(origCommit, "unlock" , "pom.xml");
        if (origCommit.join(ChangesXml.PATH).exists()) {
            Subversion.launcher(origCommit, "unlock" , ChangesXml.PATH);
        }
    }

    /** commit before deploy - because if deployment fails, we can reliably revert the commit. */
    private void promoteLocked(Log log, Map<String, String> propertyArgs, String commitTagMessage, String revertTagMessage, String commitNextMessage,
            FileNode origCommit, Maven maven) throws Exception {
        FileNode installed;

        commit(log, renderMessage(commitTagMessage));
        try {
            maven.deployOnly(log, propertyArgs, this);
        } catch (Exception e) {
            log.info("deployment failed - reverting tag");
            revertCommit(log, renderMessage(revertTagMessage));
            target.scheduleRemove(log, "deployment failed (tag has been reverted): " + e.getMessage());
            throw e;
        }

        // local install
        installed = descriptor.project.localRepo(maven);
        installed.deleteTreeOpt();
        artifacts().move(installed);

        try {
            log.info("Update pom and changes ...");
            log.debug(Subversion.launcher(origCommit, "commit", "-m", renderMessage(commitNextMessage)).exec());
            origCommit.deleteTree();
            // Move prerelease directory into REMOVE directory because it's invalid now:
            // tag was committed, and artifacts have been deployed. It's not removed immediately to make
            // distribution file available locally.
            target.scheduleRemove(log, "prerelease has been promoted");
        } catch (Exception e) {
            log.warn("Promote succeeded: your artifacts have been deployed, and your svn tag was created. ");
            log.warn("However, some post-release step failed with this exception:");
            log.warn(e);
            log.warn("Thus, you can use your release, but someone should have a look at this exception.");
        }
    }

    public String renderMessage(String message) throws SubstitutionException {
        Map<String, String> variables;

        variables = new HashMap<>();
        variables.put("revision", Long.toString(descriptor.revision));
        variables.put("release", descriptor.project.toString());
        return Substitution.ant().apply(message, variables);
    }

    //--

    public void artifactFiles(MavenProject project, MavenProjectHelper projectHelper) throws IOException {
        FileNode file;
        String type;
        String classifier;
        String[] tmp;

        for (Map.Entry<FileNode, String[]> entry : artifactFiles().entrySet()) {
            file = entry.getKey();
            tmp = entry.getValue();
            classifier = tmp[0];
            type = tmp[1];
            if ("pom".equals(type) && !project.getPackaging().equals("pom")) {
                // ignored
            } else {
                if (classifier == null) {
                    project.getArtifact().setFile(file.toPath().toFile());
                } else {
                    projectHelper.attachArtifact(project, type, classifier, file.toPath().toFile());
                }
            }
        }
    }

    public Map<FileNode, String[]> artifactFiles() throws IOException {
        FileNode artifacts;
        String name;
        String str;
        String type;
        String classifier;
        Map<FileNode, String[]> result;

        artifacts = artifacts();
        result = new HashMap<>();
        for (FileNode file : artifacts.list()) {
            name = file.getName();
            if (name.endsWith(".md5") || name.endsWith(".sha1") || name.endsWith(".asc")
                    || /* maven 3.0 */ name.equals("_maven.repositories") || /* maven 3.1 */ name.equals("_remote.repositories")) {
                // skip
            } else {
                type = file.getExtension();
                str = name.substring(0, name.length() - type.length() - 1);
                str = Strings.removeLeft(str, descriptor.project.artifactId + "-");
                str = Strings.removeLeft(str, descriptor.project.version);
                if (str.isEmpty()) {
                    classifier = null;
                } else {
                    classifier = Strings.removeLeft(str, "-");
                }
                result.put(file, new String[] { classifier, type });
            }
        }
        return result;
    }
}
