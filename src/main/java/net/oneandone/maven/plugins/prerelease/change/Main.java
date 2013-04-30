package net.oneandone.maven.plugins.prerelease.change;

import com.oneandone.devel.devreg.model.Registry;
import com.oneandone.devel.devreg.model.UnknownUserException;
import com.oneandone.devel.devreg.model.User;
import com.oneandone.devel.maven.Maven;
import net.oneandone.sushi.cli.Child;
import net.oneandone.sushi.cli.Cli;
import net.oneandone.sushi.cli.Command;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;

import java.io.IOException;

public class Main extends Cli implements Command {
    public static void main(String[] args) throws IOException {
        new Main().run(args);
    }

    private Maven maven;

    public Main() throws IOException {
        this.maven = Maven.withSettings(console.world);
    }

    @Override
    public void invoke() throws Exception {
        printHelp();
    }

    @Child("add")
    public Change add() throws IOException {
        return new Add(console, user(), maven, pom());
    }

    @Child("freeze")
    public Change freeze() throws IOException {
        return new Freeze(console, user(), maven, pom());
    }

    @Child("unfreeze")
    public Change unfreeze() throws IOException {
        return new Unfreeze(console, user(), maven, pom());
    }

    @Child("dependency")
    public Change dependency() throws IOException {
        return new Dependency(console, user(), maven, pom());
    }

    @Override
    public void printHelp() {
        console.info.println("Adjusts changes.xml of the project in your current working directory.");
        console.info.println("usage: 'change' command");
        console.info.println("Commands");
        console.info.println("  'add' ['-B'] ['-local'] ['-r'] type-and-message      Adds a new entry (and a new release when necessary)");
        console.info.println("  'freeze' ['-B'] ['-local'] date message              Marks this project as frozen.");
        console.info.println("  'unfreeze' ['-B'] ['-local']                         Removes frozen mark.");
        console.info.println("  'dependency' ['-B'] ['-local'] artifact version      Updates a dependency version.");
        console.info.println("Options:");
        console.info.println("  -B         'batch': do not ask - commit.");
        console.info.println("  -local     do not commit changes to svn; by default, all commands show a diff and offer to commit");
        console.info.println("  -r         also set the release date");
    }

    private User user() throws IOException {
        try {
            return Registry.loadCached(console.world).whoAmI();
        } catch (UnknownUserException e) {
            throw new IOException("you're unknown, please use http://wiki.intranet.1and1.com/bin/view/UE/DeveloperRegistry to register.");
        }
    }

    private MavenProject pom() throws IOException {
        try {
            return maven.loadPom(console.world.file("pom.xml"));
        } catch (ProjectBuildingException e) {
            throw new IOException("cannload load pom: " + e.getMessage(), e);
        }
    }
}