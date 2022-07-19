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

package net.minecraftforge.gradle.common.util;

import com.google.common.base.Suppliers;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class AccessTransformerUtils {
    @SuppressWarnings("UnstableApiUsage")
    public static void configureProcessResources(MinecraftExtension extension, TaskContainer tasks, Provider<File> mcpToSrgFileProvider) {
        AccessLineTransformer accessLineTransformer = new AccessLineTransformer(mcpToSrgFileProvider);
        tasks.withType(ProcessResources.class).configureEach(task -> {
            Supplier<Set<String>> atFilesSupplier = Suppliers.memoize(() -> {
                Set<String> atFiles = new HashSet<>();
                for (File atFile : extension.getAccessTransformers().getFiles()) {
                    atFiles.add(atFile.getAbsolutePath());
                }
                return atFiles;
            });

            task.eachFile(fileCopyDetails -> {
                if (atFilesSupplier.get().contains(fileCopyDetails.getFile().getAbsolutePath())) {
                    fileCopyDetails.filter(accessLineTransformer);
                }
            });
        });
    }

    public static String remapATLine(IMappingFile mappings, final String originalLine) {
        int commentIdx = originalLine.indexOf('#');
        String line;
        String comment;
        if (commentIdx != -1) {
            line = originalLine.substring(0, commentIdx).trim();
            comment = originalLine.substring(commentIdx);
        } else {
            line = originalLine.trim();
            comment = null;
        }
        if (line.isEmpty())
            return originalLine;

        String[] parts = line.split(" ");
        boolean isMember = parts.length == 3;
        if (parts.length != 2 && !isMember) {
            // Skip lines that do not fit what we expect
            return originalLine;
        }

        // We do not want to allow using '/' in the original so that only '.' works, like in the AT spec
        // Specifying a subclass already requires $, so we are good there
        String className = parts[1].replace('/', '_').replace('.', '/');
        if (isMember) {
            String memberName = parts[2];
            int parenIdx = memberName.indexOf('(');
            if (parenIdx != -1) {
                // Method
                String name = memberName.substring(0, parenIdx);
                String desc = memberName.substring(parenIdx);
                parts[2] = mappings.getClass(className).remapMethod(name, desc) + mappings.remapDescriptor(desc);
            } else {
                // Field
                parts[2] = mappings.getClass(className).remapField(memberName);
            }
        }
        parts[1] = mappings.remapClass(className).replace('/', '.');

        String joined = String.join(" ", parts);
        return comment != null ? joined + " " + comment : joined;
    }

    private static class AccessLineTransformer implements Transformer<String, String> {
        private final Provider<File> mcpToSrgFileProvider;
        private IMappingFile mappings;
        private boolean invalid;

        private AccessLineTransformer(Provider<File> mcpToSrgFileProvider) {
            this.mcpToSrgFileProvider = mcpToSrgFileProvider;
        }

        @Override
        public String transform(String line) {
            if (this.invalid)
                return line;
            if (this.mappings == null) {
                try {
                    this.mappings = IMappingFile.load(mcpToSrgFileProvider.get());
                } catch (IOException e) {
                    this.invalid = true;
                    throw new RuntimeException(e);
                }
            }

            return remapATLine(this.mappings, line);
        }
    }
}
