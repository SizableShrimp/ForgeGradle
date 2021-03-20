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

import net.minecraftforge.srgutils.IMappingFile.IClass;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IMappingFile.IPackage;
import net.minecraftforge.srgutils.IMappingFile.IParameter;
import net.minecraftforge.srgutils.IRenamer;

public class McpRenamer implements IRenamer {
    public final McpNames mcpMapper;
    private final boolean renamePackages;
    private final boolean renameClasses;
    private final boolean renameFields;
    private final boolean renameMethods;
    private final boolean renameParameters;

    private McpRenamer(McpNames mcpMapper, boolean renamePackages, boolean renameClasses, boolean renameFields, boolean renameMethods, boolean renameParameters) {
        this.mcpMapper = mcpMapper;
        this.renamePackages = renamePackages;
        this.renameClasses = renameClasses;
        this.renameFields = renameFields;
        this.renameMethods = renameMethods;
        this.renameParameters = renameParameters;
    }

    @Override
    public String rename(IPackage value) {
        return renamePackages ? mcpMapper.rename(value.getMapped()) : IRenamer.super.rename(value);
    }

    @Override
    public String rename(IClass value) {
        return renameClasses ? mcpMapper.rename(value.getMapped()) : IRenamer.super.rename(value);
    }

    @Override
    public String rename(IField value) {
        return renameFields ? mcpMapper.rename(value.getMapped()) : IRenamer.super.rename(value);
    }

    @Override
    public String rename(IMethod value) {
        return renameMethods ? mcpMapper.rename(value.getMapped()) : IRenamer.super.rename(value);
    }

    @Override
    public String rename(IParameter value) {
        return renameParameters ? mcpMapper.rename(value.getMapped()) : IRenamer.super.rename(value);
    }

    public static final class Builder {
        private final McpNames mcpMapper;
        private boolean renamePackages;
        private boolean renameClasses;
        private boolean renameFields;
        private boolean renameMethods;
        private boolean renameParameters;

        public Builder(McpNames mcpMapper) {
            this.mcpMapper = mcpMapper;
        }

        public Builder renamePackages() {
            this.renamePackages = true;
            return this;
        }

        public Builder renameClasses() {
            this.renameClasses = true;
            return this;
        }

        public Builder renameFields() {
            this.renameFields = true;
            return this;
        }

        public Builder renameMethods() {
            this.renameMethods = true;
            return this;
        }

        public Builder renameParameters() {
            this.renameParameters = true;
            return this;
        }

        public McpRenamer build() {
            return new McpRenamer(this.mcpMapper, this.renamePackages, this.renameClasses, this.renameFields, this.renameMethods, this.renameParameters);
        }
    }
}
