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

package net.minecraftforge.gradle.userdev;

import com.google.common.collect.ImmutableList;
import net.minecraftforge.gradle.common.tasks.ApplyMappings;
import net.minecraftforge.gradle.common.tasks.ApplyRangeMap;
import net.minecraftforge.gradle.common.tasks.DownloadAssets;
import net.minecraftforge.gradle.common.tasks.DownloadMCMeta;
import net.minecraftforge.gradle.common.tasks.DownloadMavenArtifact;
import net.minecraftforge.gradle.common.tasks.ExtractExistingFiles;
import net.minecraftforge.gradle.common.tasks.ExtractMCPData;
import net.minecraftforge.gradle.common.tasks.ExtractNatives;
import net.minecraftforge.gradle.common.tasks.ExtractRangeMap;
import net.minecraftforge.gradle.common.util.AccessTransformerUtils;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.EnvironmentChecks;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.MojangLicenseHelper;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.ChannelProvidersExtension;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPMappings;
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG;
import net.minecraftforge.gradle.userdev.jarjar.JarJarProjectExtension;
import net.minecraftforge.gradle.userdev.tasks.JarJar;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;
import net.minecraftforge.gradle.userdev.transforms.AccessTransformAction;
import net.minecraftforge.gradle.userdev.util.DeobfuscatingRepo;
import net.minecraftforge.gradle.userdev.util.Deobfuscator;
import net.minecraftforge.gradle.userdev.util.DependencyRemapper;
import net.minecraftforge.srgutils.IMappingFile;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class UserDevPlugin implements Plugin<Project> {
    public static final Attribute<Boolean> ACCESS_TRANSFORMED_ATTRIBUTE =
            Attribute.of("net.minecraftforge.gradle.userdev.accesstransformed", Boolean.class);

    public static final String JAR_JAR_TASK_NAME = "jarJar";
    public static final String JAR_JAR_GROUP = "jarjar";

    public static final String JAR_JAR_DEFAULT_CONFIGURATION_NAME = "jarJar";

    public static final String MINECRAFT = "minecraft";
    public static final String OBF = "__obfuscated";

    @Override
    public void apply(@Nonnull Project project) {
        EnvironmentChecks.checkEnvironment(project);
        Utils.addRepoFilters(project);

        final Logger logger = project.getLogger();
        final UserDevExtension extension = project.getExtensions().create(UserDevExtension.EXTENSION_NAME, UserDevExtension.class, project);
        project.getExtensions().create(ChannelProvidersExtension.EXTENSION_NAME, ChannelProvidersExtension.class);
        project.getPluginManager().apply(JavaPlugin.class);
        final File nativesFolder = project.file("build/natives/");

        final NamedDomainObjectContainer<RenameJarInPlace> reobfExtension = createReobfExtension(project);

        final Configuration minecraft = project.getConfigurations().create(MINECRAFT);
        project.getConfigurations().named(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
                .configure(c -> c.extendsFrom(minecraft));

        // Let gradle handle the downloading by giving it a configuration to dl. We'll focus on applying mappings to it.
        final Configuration internalObfConfiguration = project.getConfigurations().create(OBF);
        internalObfConfiguration.setDescription("Generated scope for obfuscated dependencies");

        // Create extension for dependency remapping
        // Can't create at top-level or put in `minecraft` ext due to configuration name conflict
        final Deobfuscator deobfuscator = new Deobfuscator(project, Utils.getCache(project, "deobf_dependencies"));
        final DependencyRemapper remapper = new DependencyRemapper(project, deobfuscator);
        project.getExtensions().create(DependencyManagementExtension.EXTENSION_NAME, DependencyManagementExtension.class, project, remapper);
        project.getExtensions().create(JarJarProjectExtension.EXTENSION_NAME, JarJarProjectExtension.class, project);

        final TaskContainer tasks = project.getTasks();
        final TaskProvider<DownloadMavenArtifact> downloadMcpConfig = tasks.register("downloadMcpConfig", DownloadMavenArtifact.class);
        final TaskProvider<ExtractMCPData> extractSrg = tasks.register("extractSrg", ExtractMCPData.class);
        final TaskProvider<GenerateSRG> createSrgToMcp = tasks.register("createSrgToMcp", GenerateSRG.class);
        final TaskProvider<GenerateSRG> createMcpToSrg = tasks.register("createMcpToSrg", GenerateSRG.class);
        final TaskProvider<DownloadMCMeta> downloadMCMeta = tasks.register("downloadMCMeta", DownloadMCMeta.class);
        final TaskProvider<ExtractNatives> extractNatives = tasks.register("extractNatives", ExtractNatives.class);
        final TaskProvider<DownloadAssets> downloadAssets = tasks.register("downloadAssets", DownloadAssets.class);
        final TaskProvider<DefaultTask> hideLicense = tasks.register(MojangLicenseHelper.HIDE_LICENSE, DefaultTask.class);
        final TaskProvider<DefaultTask> showLicense = tasks.register(MojangLicenseHelper.SHOW_LICENSE, DefaultTask.class);

        AccessTransformerUtils.configureProcessResources(extension, tasks, createMcpToSrg.flatMap(task -> task.getOutput().getAsFile()));
        registerAccessTransform(project, extension, createSrgToMcp);

        hideLicense.configure(task -> task.doLast(_task ->
                MojangLicenseHelper.hide(project, extension.getMappingChannel().get(), extension.getMappingVersion().get())));

        showLicense.configure(task -> task.doLast(_t ->
                MojangLicenseHelper.show(project, extension.getMappingChannel().get(), extension.getMappingVersion().get())));

        extractSrg.configure(task -> task.getConfig().set(downloadMcpConfig.flatMap(DownloadMavenArtifact::getOutput)));

        createSrgToMcp.configure(task -> {
            task.setReverse(false);
            task.getSrg().set(extractSrg.flatMap(ExtractMCPData::getOutput));
            task.getMappings().set(extension.getMappings());
            task.getFormat().set(IMappingFile.Format.SRG);
            task.getOutput().set(project.getLayout().getBuildDirectory()
                    .dir(task.getName()).map(s -> s.file("output.srg")));
        });

        createMcpToSrg.configure(task -> {
            task.setReverse(true);
            task.getSrg().set(extractSrg.flatMap(ExtractMCPData::getOutput));
            task.getMappings().set(extension.getMappings());
        });

        extractNatives.configure(task -> {
            task.getMeta().set(downloadMCMeta.flatMap(DownloadMCMeta::getOutput));
            task.getOutput().set(nativesFolder);
        });
        downloadAssets.configure(task -> task.getMeta().set(downloadMCMeta.flatMap(DownloadMCMeta::getOutput)));

        final boolean doingUpdate = project.hasProperty("UPDATE_MAPPINGS");
        final String updateVersion = doingUpdate ? (String) project.property("UPDATE_MAPPINGS") : null;
        final String updateChannel = doingUpdate
                ? (project.hasProperty("UPDATE_MAPPINGS_CHANNEL") ? (String) project.property("UPDATE_MAPPINGS_CHANNEL") : "snapshot")
                : null;
        if (doingUpdate) {
            logger.lifecycle("This process uses Srg2Source for java source file renaming. Please forward relevant bug reports to https://github.com/MinecraftForge/Srg2Source/issues.");

            final TaskProvider<JavaCompile> javaCompile = tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class);
            final JavaPluginExtension javaConv = project.getExtensions().getByType(JavaPluginExtension.class);
            final Property<FileCollection> mainJavaSources = project.getObjects().property(FileCollection.class);
            mainJavaSources.finalizeValueOnRead();
            mainJavaSources.set(project.getProviders().gradleProperty("UPDATE_SOURCESETS")
                    .orElse(SourceSet.MAIN_SOURCE_SET_NAME)
                    .map(s -> s.split(";"))
                    .flatMap(sourceSets -> Stream.of(sourceSets)
                            .map(sourceSet -> javaConv.getSourceSets().named(sourceSet)
                                    .map(SourceSet::getJava)
                                    .map(SourceDirectorySet::getSourceDirectories))
                            .reduce((a, b) -> a.zip(b, FileCollection::plus)).get()));

            final TaskProvider<DownloadMCPMappings> dlMappingsNew = tasks.register("downloadMappingsNew", DownloadMCPMappings.class);
            final TaskProvider<ExtractRangeMap> extractRangeConfig = tasks.register("extractRangeMap", ExtractRangeMap.class);
            final TaskProvider<ApplyRangeMap> applyRangeConfig = tasks.register("applyRangeMap", ApplyRangeMap.class);
            final TaskProvider<ApplyMappings> toMCPNew = tasks.register("srg2mcpNew", ApplyMappings.class);
            final TaskProvider<ExtractExistingFiles> extractMappedNew = tasks.register("extractMappedNew", ExtractExistingFiles.class);
            final TaskProvider<DefaultTask> updateMappings = tasks.register("updateMappings", DefaultTask.class);

            extractRangeConfig.configure(task -> {
                task.getSources().from(mainJavaSources);
                task.getDependencies().from(javaCompile.map(JavaCompile::getClasspath));
            });

            applyRangeConfig.configure(task -> {
                task.getRangeMap().set(extractRangeConfig.flatMap(ExtractRangeMap::getOutput));
                task.getSrgFiles().from(createMcpToSrg.flatMap(GenerateSRG::getOutput));
                task.getSources().from(mainJavaSources);
            });

            dlMappingsNew.configure(task -> {
                task.getMappings().set(updateChannel + "_" + updateVersion);
                task.getOutput().set(project.getLayout().getBuildDirectory().file("mappings_new.zip"));
            });

            toMCPNew.configure(task -> {
                task.getInput().set(applyRangeConfig.flatMap(ApplyRangeMap::getOutput));
                task.getMappings().set(dlMappingsNew.flatMap(DownloadMCPMappings::getOutput));
            });

            extractMappedNew.configure(task -> {
                task.getArchive().set(toMCPNew.flatMap(ApplyMappings::getOutput));
                task.getTargets().from(mainJavaSources);
            });

            updateMappings.configure(task -> task.dependsOn(extractMappedNew));
        }

        configureJarJarTask(project);

        project.afterEvaluate(p -> {
            MinecraftUserRepo mcrepo = null;
            DeobfuscatingRepo deobfrepo = null;

            DependencySet mcDependencies = minecraft.getDependencies();
            for (Dependency dep : new ArrayList<>(mcDependencies)) { // Copied to new list to avoid ConcurrentModificationException
                if (!(dep instanceof ExternalModuleDependency)) {
                    throw new IllegalArgumentException(minecraft.getName() + " configuration must contain a Maven dependency");
                }
                if (mcrepo != null) {
                    throw new IllegalArgumentException(minecraft.getName() + " configuration must contain exactly one dependency");
                }
                mcDependencies.remove(dep);

                mcrepo = new MinecraftUserRepo(p, dep.getGroup(), dep.getName(), dep.getVersion(), new ArrayList<>(extension.getAccessTransformers().getFiles()), extension.getMappings().get());
                String newDep = mcrepo.getDependencyString();
                //p.getLogger().lifecycle("New Dep: " + newDep);
                ExternalModuleDependency ext = (ExternalModuleDependency) p.getDependencies().create(newDep);
                // Request the AT attribute as TRUE, so it gets transformed by our transform action
                ext.attributes(attributes -> attributes.attribute(ACCESS_TRANSFORMED_ATTRIBUTE, Boolean.TRUE));

                if (MinecraftUserRepo.CHANGING_USERDEV) {
                    ext.setChanging(true);
                }
                minecraft.resolutionStrategy(strat -> strat.cacheChangingModulesFor(10, TimeUnit.SECONDS));

                minecraft.getDependencies().add(ext);
            }
            if (mcrepo == null) {
                throw new IllegalStateException("Missing '" + minecraft.getName() + "' dependency.");
            }

            project.getRepositories().maven(e -> {
                e.setUrl(Utils.FORGE_MAVEN);
                e.metadataSources(m -> {
                    m.gradleMetadata();
                    m.mavenPom();
                    m.artifact();
                });
            });

            if (!internalObfConfiguration.getDependencies().isEmpty()) {
                deobfrepo = new DeobfuscatingRepo(project, internalObfConfiguration, deobfuscator);
                if (deobfrepo.getResolvedOrigin() == null) {
                    project.getLogger().error("DeobfRepo attempted to resolve an origin repo early but failed, this may cause issues with some IDEs");
                }
            }
            remapper.attachMappings(extension.getMappings().get());

            // We have to add these AFTER our repo so that we get called first, this is annoying...
            new BaseRepo.Builder()
                    .add(mcrepo)
                    .add(deobfrepo)
                    .add(MCPRepo.create(project))
                    .add(MinecraftRepo.create(project)) //Provides vanilla extra/slim/data jars. These don't care about OBF names.
                    .attach(project);

            MojangLicenseHelper.displayWarning(p, extension.getMappingChannel().get(), extension.getMappingVersion().get(), updateChannel, updateVersion);

            project.getRepositories().maven(e -> {
                e.setUrl(Utils.MOJANG_MAVEN);
                e.metadataSources(MetadataSources::artifact);
            });
            project.getRepositories().mavenCentral(); //Needed for MCP Deps
            mcrepo.validate(minecraft, extension.getRuns().getAsMap(), extractNatives.get(), downloadAssets.get(), createSrgToMcp.get()); //This will set the MC_VERSION property.

            ExtraPropertiesExtension extraProperties = project.getExtensions().getExtraProperties();
            String mcVer = (String) extraProperties.get("MC_VERSION");
            String mcpVer = extraProperties.has("MCP_VERSION") ? (String) extraProperties.get("MCP_VERSION") : null;
            if (mcpVer != null) {
                downloadMcpConfig.configure(t -> t.setArtifact(Utils.getMCPConfigArtifact(mcpVer)));
            } else {
                extractSrg.configure(task -> {
                    task.getConfig().set((File) null);
                    task.getVersion().set(mcVer);
                });
            }
            downloadMCMeta.configure(t -> t.getMCVersion().convention(mcVer));

            // Register reobfJar for the 'jar' task
            reobfExtension.create(JavaPlugin.JAR_TASK_NAME);

            String assetIndex = mcVer;

            try {
                // Check meta exists
                final DownloadMCMeta downloadMCMetaTask = downloadMCMeta.get();
                final File metaOutput = downloadMCMetaTask.getOutput().get().getAsFile();
                if (!metaOutput.exists()) {
                    // Force download meta
                    downloadMCMetaTask.downloadMCMeta();
                }

                VersionJson json = Utils.loadJson(metaOutput, VersionJson.class);

                assetIndex = json.assetIndex.id;
            } catch (IOException e) {
                project.getLogger().warn("Failed to retrieve asset index ID", e);
            }

            // Finalize asset index
            final String finalAssetIndex = assetIndex;

            extension.getRuns().forEach(runConfig -> runConfig.token("asset_index", finalAssetIndex));
            Utils.createRunConfigTasks(extension, extractNatives, downloadAssets, createSrgToMcp);
        });

        project.afterEvaluate(projectAfter -> {
            projectAfter.getTasks().withType(JarJar.class).all(jarJar -> {
                if (jarJar.isEnabled()) {
                    reobfExtension.create(jarJar.getName());
                }
            });
        });
    }

    private NamedDomainObjectContainer<RenameJarInPlace> createReobfExtension(Project project) {
        final JavaPluginExtension javaConv = project.getExtensions().getByType(JavaPluginExtension.class);
        final NamedDomainObjectContainer<RenameJarInPlace> reobfExtension = project.container(RenameJarInPlace.class, jarName -> {
            String name = StringUtils.capitalize(jarName);

            final RenameJarInPlace task = project.getTasks().maybeCreate("reobf" + name, RenameJarInPlace.class);

            task.getClasspath().from(javaConv.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getCompileClasspath));
            task.getMappings().set(project.getTasks().named("createMcpToSrg", GenerateSRG.class).flatMap(GenerateSRG::getOutput));

            project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(t -> t.dependsOn(task));

            final TaskProvider<Jar> jarTask = project.getTasks().named(jarName, Jar.class);
            task.getInput().set(jarTask.flatMap(AbstractArchiveTask::getArchiveFile));

            return task;
        });
        project.getExtensions().add("reobf", reobfExtension);
        return reobfExtension;
    }

    protected void configureJarJarTask(Project project) {
        final Configuration configuration = project.getConfigurations().create(JAR_JAR_DEFAULT_CONFIGURATION_NAME);

        JavaPluginExtension extension = project.getExtensions().getByType(JavaPluginExtension.class);
        project.getTasks().register(JAR_JAR_TASK_NAME, JarJar.class, jarJar -> {
            jarJar.setGroup(JAR_JAR_GROUP);
            jarJar.setDescription("Create a combined JAR of project and selected dependencies");
            jarJar.getArchiveClassifier().convention("all");

            jarJar.getManifest().inheritFrom(((Jar) project.getTasks().getByName("jar")).getManifest());
            jarJar.from(extension.getSourceSets().getByName("main").getOutput());

            jarJar.configuration(configuration);

            jarJar.setEnabled(false);
        });

        project.getArtifacts().add(JAR_JAR_DEFAULT_CONFIGURATION_NAME, project.getTasks().named(JAR_JAR_TASK_NAME));
    }

    private static void registerAccessTransform(Project project, MinecraftExtension extension, TaskProvider<GenerateSRG> createSrgToMcp) {
        final DependencyHandler dependencies = project.getDependencies();

        final Provider<String> toolCoordinates = project.getProviders().provider(() -> Utils.ACCESSTRANSFORMER);
        //noinspection ConstantConditions Provider#map accepts a Transformer which returns null
        final Provider<File> toolFile = toolCoordinates.map(toolStr -> MavenArtifactDownloader.gradle(project, toolStr, false));

        final Provider<String> mainClass = toolFile.map(file -> {
            // Locate main class in jar file
            try (JarFile jarFile = new JarFile(file)) {
                return jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to get main class attribute from tool jar " + file, e);
            }
        });

        dependencies.attributesSchema(attributesSchema -> attributesSchema.attribute(ACCESS_TRANSFORMED_ATTRIBUTE));

        // Default value of attribute for jar artifact types is FALSE
        dependencies.artifactTypes(artifactTypes ->
                artifactTypes.getByName(ArtifactTypeDefinition.JAR_TYPE, def ->
                        def.getAttributes().attribute(ACCESS_TRANSFORMED_ATTRIBUTE, Boolean.FALSE)));

        dependencies.registerTransform(AccessTransformAction.class, spec -> {
            //noinspection UnstableApiUsage
            spec.getFrom().attribute(ACCESS_TRANSFORMED_ATTRIBUTE, Boolean.FALSE).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            //noinspection UnstableApiUsage
            spec.getTo().attribute(ACCESS_TRANSFORMED_ATTRIBUTE, Boolean.TRUE).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            spec.parameters(parameters -> {
                parameters.getAccessTransformers().from(extension.getAccessTransformers());
                parameters.getClasspath().from(toolFile);
                parameters.getMappings().set(createSrgToMcp.flatMap(GenerateSRG::getOutput));
                parameters.getATArgumentPrefix().set("--atFile");
                parameters.getArguments().value(ImmutableList.of("--inJar", "{input}", "--outJar", "{output}"));
                parameters.getMainClass().set(mainClass);
            });
        });
    }
}
