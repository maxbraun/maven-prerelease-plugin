![Build Status @ Travis](https://api.travis-ci.org/maxbraun/maven-prerelease-plugin.svg)
# Maven Prerelease Plugin

## Overview

The Prerelease Plugin is an alternative to Maven's Release Plugin. It aims for
* improved performance: build you project once, not twice
* improved robustness: roll back automatically if a release fails
* support for continuous delivery: test snapshots, and promote them to releases.

Limitations
* no multi-modules
* no site generation
* svn-only
* tag and next development version not queried interactively
* Java 7 only

Release Plugin and Prerelease plugin can be used interchangingly: some users may prefer to stick to the offical release plugin, others may switch to the Prerelease plugin.

[Maven Site](http://mlhartme.github.io/maven-prerelease-plugin/plugin-info.html)

## Quickstart

To create a release:

   * enter your project directory
   * run `mvn net.oneandone.maven.plugins:prerelease:update-promote`.

The resulting release is similar to `mvn -B release:prepare release:perform`.

## Prerelease

A prerelease is a directory on your local disk, usually in `~/.m2/prereleases`. It contains

   * an uncommitted tag
   * a local repository containing the artifacts that comprise this prerelease.
   * prerelease.propertiers with metadata

The prerelease directory has a unique name which is the svn revision of the last change contained in this prerelease.

## Usage

Basically, a prerelease can be created with `prerelease:create` and promoted with `prerelease:promote`. This is roughly comparable to Maven's `release:prepare` and `release:perform`. Create performs various checks and creates the prerelease directory. Promoting takes an existing prerelease and turns it into a normal release by comming the tag and deploying it's artifacts. 

Instead of the create goal, you can use `prerelease:update`, which creates the prerelease only if its necessary; creation is skipped if there is already a prerelese for the revision of the last modification.

Finally, you cam create a release in one single step: `prerelease:update-promote`.

## Implementation

A prerelease is basically created by running "mvn install", it's promoted by running the remaining plugins for the "deploy phase. The plugin calls Maven directly in Java, not as a separate process. A MavenExecutionListener is used record the necessary state after the "install" step. This state (which is currently the list of artifacts attached to the projects, and the Project properties defined by other plugins (like build numbers)) is used for prerelease:promote to resume the build after the install step and run only the deploy goal.

The plugin invokes `svn` command-line tools, you need it in your path.

## Jenkins Ram disk support

If you keep your Jenkins jobs and your .m2 directory in a ram disk, all prereleases will also end up in this ram disk (because the default location is "${settings.localRepository}/../prereleases"). This consumes quite a lot of disk space, especially if you keep more than 1 prerelease.

With "-Dprerelease.storages=/path/to/ramdisk/prereleases,/path/to/harddisk/prereleases" you can define a primary and secondary storage for prereleases. Define this property for whenever you invoke the prerelease plugin on Jenkins. New prereleases are created in primary storage. Later, as a separate job in your Jenkins, you run "prerelease:swap". This will move all prereleases to secondary storage. This will slightly slow down releases when the reprerelease is loaded from hard disk. But if there is not prerelease yet, it's as fast as before, because the new prerelease starts life on the ram disk.
