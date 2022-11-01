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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class MinecraftPrepareRunTask extends DefaultTask {
    @Input
    @Optional
    public abstract Property<Boolean> getGenerateBslConfig();

    @Input
    @Optional
    protected abstract MapProperty<String, String> getSystemProperties();

    @Input
    @Optional
    protected abstract ListProperty<String> getLaunchArguments();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getBslConfigOutput();

    public MinecraftPrepareRunTask() {
        this.setGroup(RunConfig.RUNS_GROUP);
        this.setImpliesSubProjects(true); // Preparing the game in the current project and child projects is a bad idea
    }

    @TaskAction
    public void exec() throws IOException {
        if (getGenerateBslConfig().getOrElse(false) == Boolean.TRUE && getBslConfigOutput().isPresent()) {
            Map<String, String> systemProperties = getSystemProperties().get();
            List<String> launchArgs = getLaunchArguments().get();

            List<String> output = new ArrayList<>();

            systemProperties.forEach((k, v) -> {
                if (!"bsl.config".equals(k))
                    output.add(String.format("system_property: %s=%s", k, v));
            });
            launchArgs.forEach(arg -> output.add(String.format("arg: %s", arg)));

            Files.write(getBslConfigOutput().get().getAsFile().toPath(), output);
        }
    }
}
