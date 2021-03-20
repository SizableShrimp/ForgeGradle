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

package net.minecraftforge.gradle.mcp.task;

import com.google.common.collect.Lists;
import net.minecraftforge.gradle.common.util.MappingUtil;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.mcp.MCPRepo;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DownloadMCPMappingsTask extends DefaultTask {

    private String mappings;
    private List<File> output = Lists.newArrayList(getProject().file("build/mappings.zip"));

    @Input
    public String getMappings() {
        return this.mappings;
    }

    @OutputFiles
    public List<File> getOutput() {
        return output;
    }

    public void setMappings(String value) {
        this.mappings = value;
    }

    public void setOutput(List<File> output) {
        this.output = output;
    }

    @TaskAction
    public void download() throws IOException {
        List<File> out = getMappingFiles();
        this.setDidWork(out != null && out.stream().allMatch(File::exists));

        boolean allEquals = true;
        for (int i = 0; i < out.size(); i++) {
            if (!FileUtils.contentEquals(out.get(i), output.get(i))) {
                allEquals = false;
                break;
            }
        }
        if (allEquals) return;
        for (int i = 0; i < out.size(); i++) {
            File outFile = out.get(i);
            File destinationFile = output.get(i);
            if (destinationFile.exists()) destinationFile.delete();
            if (!destinationFile.getParentFile().exists()) destinationFile.getParentFile().mkdirs();
            FileUtils.copyFile(outFile, destinationFile);
        }
    }

    private List<File> getMappingFiles() {
        return getMappingFiles(getProject(), getMappings());
    }

    public static List<File> getMappingFiles(Project project, String mappings) {
        int idx = mappings.lastIndexOf('_');
        if (idx == -1)
            throw new IllegalArgumentException("Invalid mapping string format, must be {channel}_{version}.");

        String channel = mappings.substring(0, idx);
        String version = mappings.substring(idx + 1);
        return MappingUtil.getMappingResult(channel, version, (c, v) -> getMappingFile(project, c, v));
    }

    private static File getMappingFile(Project project, String channel, String version) {
        String artifact = MCPRepo.getMappingDep(channel, version);
        File ret = MavenArtifactDownloader.generate(project, artifact, false);
        if (ret == null)
            throw new IllegalStateException("Failed to download mappings: " + artifact);
        return ret;
    }
}
