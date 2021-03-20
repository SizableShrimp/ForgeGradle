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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MappingUtil {
    public static boolean isMappingMerged(String mapping) {
        if (mapping == null)
            return false;
        int idx = mapping.lastIndexOf('_');
        if (idx == -1)
            return false;
        return isChannelMerged(mapping.substring(0, idx));
    }

    public static boolean isChannelMerged(String channel) {
        return "official_snapshot".equals(channel) || "official_stable".equals(channel);
    }

    public static ImmutableList<String> extractOfficialMappingSeparated(String mapping) {
        if (mapping == null)
            return null;

        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        return extractOfficialMappingSeparated(channel, version);
    }

    public static ImmutableList<String> extractOfficialMappingSeparated(String channel, String version) {
        if (!isChannelMerged(channel))
            return ImmutableList.of(channel, version);

        String mcversion = version.substring(version.lastIndexOf('-') + 1); // Also works if '-' is not in the string
        return ImmutableList.of("official", mcversion);
    }

    public static ImmutableList<String> extractMcpMappingSeparated(String mapping) {
        if (mapping == null)
            return null;

        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        return extractMcpMappingSeparated(channel, version);
    }

    public static ImmutableList<String> extractMcpMappingSeparated(String channel, String version) {
        if (!isChannelMerged(channel))
            return ImmutableList.of(channel, version);

        String mcpChannel = channel.substring("official_".length());
        return ImmutableList.of(mcpChannel, version);
    }

    public static <T> List<T> getMappingResult(String channel, String version, BiFunction<String, String, T> func) {
        if (!isChannelMerged(channel))
            return Lists.newArrayList(func.apply(channel, version));

        ImmutableList<String> officialMapping = extractOfficialMappingSeparated(channel, version);
        ImmutableList<String> mcpMapping = extractMcpMappingSeparated(channel, version);
        return Stream.of(officialMapping, mcpMapping).map(l -> func.apply(l.get(0), l.get(1))).collect(Collectors.toList());
    }
}
