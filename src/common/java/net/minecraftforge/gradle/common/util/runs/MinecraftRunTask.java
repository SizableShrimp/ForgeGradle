/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.util.runs;

import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;
import java.util.function.Supplier;

@DisableCachingByDefault(because = "Running Minecraft cannot be cached")
public abstract class MinecraftRunTask extends JavaExec {
    public MinecraftRunTask() {
        this.setGroup(RunConfig.RUNS_GROUP);
        this.setImpliesSubProjects(true); // Running the game in the current project and child projects is a bad idea
    }

    @TaskAction
    @Override
    public void exec() {
        Project project = this.getProject();
        JavaToolchainService javaToolchainService = this.getJavaToolchainService();
        Provider<JavaLauncher> launcherProvider = javaToolchainService.launcherFor(project.getExtensions().getByType(JavaPluginExtension.class).getToolchain());
        RunConfig runConfig = this.getRunConfig().get();
        File workDir = new File(runConfig.getWorkingDirectory());

        if (!workDir.exists() && !workDir.mkdirs())
            throw new RuntimeException("Could not create working directory: " + workDir.getAbsolutePath());

        Map<String, Supplier<String>> updatedTokens = RunConfigGenerator.configureTokensLazy(project, runConfig,
                RunConfigGenerator.mapModClassesToGradle(project, runConfig),
                this.getMinecraftArtifacts().getFiles(), this.getRuntimeClasspathArtifacts().getFiles());

        this.setWorkingDir(workDir);
        // this.getMainClass().set(runConfig.getMain());
        this.setExecutable(launcherProvider.get().getExecutablePath().getAsFile().getAbsolutePath());
        this.args(RunConfigGenerator.getArgsStream(runConfig, updatedTokens, false).toArray());
        runConfig.getJvmArgs().forEach(arg -> this.jvmArgs(runConfig.replace(updatedTokens, arg)));
        if (runConfig.isClient()) {
            getAdditionalClientArgs().get().forEach(arg -> this.jvmArgs(runConfig.replace(updatedTokens, arg)));
        }
        runConfig.getEnvironment().forEach((key, value) -> this.environment(key, runConfig.replace(updatedTokens, value)));
        runConfig.getProperties().forEach((key, value) -> this.systemProperty(key, runConfig.replace(updatedTokens, value)));

        runConfig.getAllSources().stream().map(SourceSet::getRuntimeClasspath).forEach(this::classpath);

        super.exec();
    }

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Input
    public abstract Property<RunConfig> getRunConfig();

    @Input
    public abstract ListProperty<String> getAdditionalClientArgs();

    @InputFiles
    public abstract ConfigurableFileCollection getMinecraftArtifacts();

    @InputFiles
    public abstract ConfigurableFileCollection getRuntimeClasspathArtifacts();
}
