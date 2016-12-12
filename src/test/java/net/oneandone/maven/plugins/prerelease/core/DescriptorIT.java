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

import static org.junit.Assert.assertEquals;

import java.net.URI;

import net.oneandone.maven.plugins.prerelease.util.Scm;
import org.apache.maven.project.MavenProject;
import org.junit.Ignore;
import org.junit.Test;

import net.oneandone.maven.plugins.prerelease.util.IntegrationBase;
import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.sushi.fs.file.FileNode;

public class DescriptorIT extends IntegrationBase {
    @Test
    public void normal() throws Exception {
        FileNode dir;
        Maven maven;
        MavenProject project;
        long revision;
        Descriptor descriptor;
        Scm scm;

        dir = checkoutProject("minimal");
        maven = maven(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        scm = Scm.create(project, Scm.Credentials.NONE());
        revision = scm.createWorkingCopy(dir).revision();
        descriptor = Descriptor.checkedCreate(WORLD, "foo", project, revision, false, true, scm);
        assertEquals(revision, descriptor.revision);
        assertEquals("1.0.0-SNAPSHOT", descriptor.previous);
        assertEquals("minimal", descriptor.project.artifactId);
        assertEquals("net.oneandone.maven.plugins.prerelease", descriptor.project.groupId);
        assertEquals("1.0.0", descriptor.project.version);
        assertEquals("1.0.1-SNAPSHOT", descriptor.next);
        assertEquals(REPOSITORY_URL + "/minimal/trunk", descriptor.svnOrig);
        assertEquals(REPOSITORY_URL + "/minimal/tags/minimal-1.0.0", descriptor.svnTag);
    }

    @Test(expected = TagAlreadyExists.class)
    public void tagAlreadyException() throws Exception {
        FileNode dir;
        Maven maven;
        MavenProject project;
        long revision;
        URI tag;
        Scm scm;

        dir = checkoutProject("minimal");
        maven = maven(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        scm = Scm.create(project, Scm.Credentials.NONE());
        revision = scm.createWorkingCopy(dir).revision();
        Descriptor.checkedCreate(WORLD, "foo", project, revision, false, true, scm);
        tag = new URI(REPOSITORY_URL + "/minimal/tags/minimal-1.0.0");
        svnMkdir(tag);
        try {
            Descriptor.checkedCreate(WORLD, "foo", project, revision, false, true, scm);
        } finally {
            svnRemove(tag);
        }
    }

    @Test(expected = VersioningProblem.class)
    @Ignore  // TODO
    public void parentSnapshot() throws Exception {
        FileNode dir;
        Maven maven;
        MavenProject project;
        long revision;
        Scm scm;

        dir = checkoutProject("parentSnapshot");
        maven = maven(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        scm = Scm.create(project, Scm.Credentials.NONE());
        revision = scm.createWorkingCopy(dir).revision();
        Descriptor.checkedCreate(WORLD, "foo", project, revision, false, true, scm);
    }

    @Test(expected = VersioningProblem.class)
    public void dependencySnapshot() throws Exception {
        FileNode dir;
        Maven maven;
        MavenProject project;
        long revision;
        Scm scm;

        dir = checkoutProject("dependencySnapshot");
        maven = maven(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        scm = Scm.create(project, Scm.Credentials.NONE());
        revision = scm.createWorkingCopy(dir).revision();
        Descriptor.checkedCreate(WORLD, "foo", project, revision, false, true, scm);
    }

    @Test(expected = VersioningProblem.class)
    public void pluginSnapshot() throws Exception {
        FileNode dir;
        Maven maven;
        MavenProject project;
        long revision;
        Scm scm;

        dir = checkoutProject("pluginSnapshot");
        maven = maven(WORLD);
        project = maven.loadPom(dir.join("pom.xml"));
        scm = Scm.create(project, Scm.Credentials.NONE());
        revision = scm.createWorkingCopy(dir).revision();
        Descriptor.checkedCreate(WORLD, "foo", project, revision, false, true, scm);
    }
}
