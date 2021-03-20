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

package net.minecraftforge.gradle.userdev.tasks;

import com.google.common.io.ByteStreams;
import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.Utils;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class RenameJarSrg2Mcp extends JarExec {
    private static final Pattern EXTENSION = Pattern.compile("\\.\\w+$");
    private Supplier<File> input;
    private File output;
    private Supplier<List<File>> mappings;
    private boolean signatureRemoval;

    public RenameJarSrg2Mcp() {
        tool = Utils.INSTALLERTOOLS;
        args = new String[] { "--task", "SRG_TO_MCP", "--input", "{input}", "--output", "{output}", "--mcp", "{mappings}", "{strip}"};
    }

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());
        getProject().getLogger().lifecycle("remap jar mappings: {}", getJoinedMappings().getAbsolutePath());
        replace.put("{mappings}", getJoinedMappings().getAbsolutePath());
        replace.put("{strip}", getSignatureRemoval() ? "--strip-signatures" : "");

        return Arrays.stream(getArgs()).map(arg -> replace.getOrDefault(arg, arg)).filter(it -> !it.isEmpty()).collect(Collectors.toList());
    }

    private File getJoinedMappings() {
        List<File> mappings = getMappings();
        if (mappings.size() == 1)
            return mappings.get(0);
        File outputMapping = new File(EXTENSION.matcher(mappings.get(0).getAbsolutePath()).replaceFirst("-joined$0"));
        try {
            HashStore store = new HashStore()
                    .add(mappings)
                    .load(new File(outputMapping.getAbsolutePath() + ".input"));
            if (!store.isSame() || !outputMapping.exists()) {
                try (JarOutputStream outs = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputMapping))); ZipFile official = new ZipFile(mappings.get(0)); ZipFile mcp =
                        new ZipFile(mappings.get(1))) {
                    for (ZipEntry e : Collections.list(official.entries())) {
                        if (e.isDirectory()) {
                            outs.putNextEntry(e);
                        } else {
                            ZipEntry n = new ZipEntry(e.getName());
                            n.setTime(e.getTime());
                            outs.putNextEntry(n);
                            ByteStreams.copy(official.getInputStream(e), outs);
                            outs.closeEntry();
                        }
                    }
                    ZipEntry entry = mcp.getEntry("params.csv");
                    if (entry != null) {
                        ZipEntry n = new ZipEntry(entry.getName());
                        n.setTime(entry.getTime());
                        outs.putNextEntry(n);
                        ByteStreams.copy(mcp.getInputStream(entry), outs);
                        outs.closeEntry();
                    }
                }
            }
            return outputMapping;
        } catch (IOException e) {
            getProject().getLogger().error("Error when joining mappings to remap jar srg2mcp. Defaulting to first provided file.", e);
            return mappings.get(0);
        }
    }

    public boolean getSignatureRemoval() {
        return this.signatureRemoval;
    }

    public void setSignatureRemoval(boolean value) {
        this.signatureRemoval = value;
    }

    @InputFiles
    public List<File> getMappings() {
        return mappings.get();
    }
    public void setMappings(List<File> value) {
        this.mappings = () -> value;
    }
    public void setMappings(Supplier<List<File>> value) {
        this.mappings = value;
    }

    @InputFile
    public File getInput() {
        return input.get();
    }
    public void setInput(File value) {
        this.input = () -> value;
    }
    public void setInput(Supplier<File> value) {
        this.input = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}
