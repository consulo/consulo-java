package com.intellij.java.compiler.impl.javaCompiler;

import com.intellij.java.compiler.impl.javaCompiler.annotationProcessing.ProcessorConfigProfile;
import com.intellij.java.compiler.impl.javaCompiler.annotationProcessing.impl.ProcessorConfigProfileImpl;
import consulo.annotation.DeprecationInfo;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdom.Element;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.*;

@Singleton
@State(
    name = "JavaCompilerConfiguration",
    storages = {
        @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/compiler.xml")
    })
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class JavaCompilerConfiguration implements PersistentStateComponent<Element> {
    @Nonnull
    public static JavaCompilerConfiguration getInstance(@Nonnull Project project) {
        return ServiceManager.getService(project, JavaCompilerConfiguration.class);
    }

    public static final String ANNOTATION_PROCESSING = "annotation-processing";
    public static final String BYTECODE_TARGET_LEVEL = "bytecode-target-level";

    public static final String ADD_NOTNULL_ASSERTIONS = "add-not-null-assertions";
    public static final String ENTRY = "entry";
    public static final String NAME = "name";
    public static final String ENABLED = "enabled";
    public static final String MODULE = "module";
    public static final String TARGET_ATTRIBUTE = "target";

    public static final String DEFAULT_COMPILER = "JavacCompiler";

    @Nonnull
    private final Project myProject;

    private BackendCompiler myBackendCompilerCache;

    private final ProcessorConfigProfile myDefaultProcessorsProfile = new ProcessorConfigProfileImpl("Default");
    private final List<ProcessorConfigProfile> myModuleProcessorProfiles = new ArrayList<>();

    // the map is calculated by module processor profiles list for faster access to module settings
    private Map<Module, ProcessorConfigProfile> myProcessorsProfilesMap = null;
    private boolean myAddNotNullAssertions;

    @Nullable
    private String myBytecodeTargetLevel = null;  // null means compiler default

    @Deprecated
    @DeprecationInfo("We used JavaModuleExtension for store info about bytecode version")
    private final Map<String, String> myModuleBytecodeTarget = new HashMap<>();

    @Inject
    public JavaCompilerConfiguration(@Nonnull Project project) {
        myProject = project;
    }

    @Nullable
    @RequiredReadAction
    public String getBytecodeTargetLevel(@Nonnull Module module) {
        String level = myModuleBytecodeTarget.get(module.getName());
        if (level != null) {
            return level.isEmpty() ? null : level;
        }

        JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
        if (extension != null) {
            String bytecodeVersion = extension.getBytecodeVersion();
            if (bytecodeVersion != null) {
                return StringUtil.nullize(bytecodeVersion);
            }
        }
        return myBytecodeTargetLevel;
    }

    @Nonnull
    public BackendCompiler getActiveCompiler() {
        if (myBackendCompilerCache == null) {
            myBackendCompilerCache = findCompiler(DEFAULT_COMPILER);
        }
        return myBackendCompilerCache;
    }

    public void setActiveCompiler(@Nonnull BackendCompiler key) {
        myBackendCompilerCache = key;
    }

    @Nullable
    public BackendCompiler findCompiler(@Nonnull String className) {
        for (BackendCompiler backendCompiler : BackendCompiler.EP_NAME.getExtensionList(myProject)) {
            if (className.equals(backendCompiler.getClass().getSimpleName())) {
                return backendCompiler;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Element getState() {
        Element parentNode = new Element("state");

        Element annotationProcessingSettings = addChild(parentNode, ANNOTATION_PROCESSING);
        Element defaultProfileElem = addChild(annotationProcessingSettings, "profile").setAttribute("default", "true");
        AnnotationProcessorProfileSerializer.writeExternal(myDefaultProcessorsProfile, defaultProfileElem);
        for (ProcessorConfigProfile profile : myModuleProcessorProfiles) {
            Element profileElem = addChild(annotationProcessingSettings, "profile").setAttribute("default", "false");
            AnnotationProcessorProfileSerializer.writeExternal(profile, profileElem);
        }

        parentNode.setAttribute(
            "compiler",
            myBackendCompilerCache == null ? DEFAULT_COMPILER : myBackendCompilerCache.getClass().getSimpleName()
        );
        if (myAddNotNullAssertions) {
            parentNode.setAttribute(ADD_NOTNULL_ASSERTIONS, String.valueOf(Boolean.TRUE));
        }

        if (!StringUtil.isEmpty(myBytecodeTargetLevel) || !myModuleBytecodeTarget.isEmpty()) {
            Element bytecodeTarget = addChild(parentNode, BYTECODE_TARGET_LEVEL);
            if (!StringUtil.isEmpty(myBytecodeTargetLevel)) {
                bytecodeTarget.setAttribute(TARGET_ATTRIBUTE, myBytecodeTargetLevel);
            }
            if (!myModuleBytecodeTarget.isEmpty()) {
                List<String> moduleNames = new ArrayList<>(myModuleBytecodeTarget.keySet());
                Collections.sort(moduleNames, String.CASE_INSENSITIVE_ORDER);
                for (String name : moduleNames) {
                    Element moduleElement = addChild(bytecodeTarget, MODULE);
                    moduleElement.setAttribute(NAME, name);
                    String value = myModuleBytecodeTarget.get(name);
                    moduleElement.setAttribute(TARGET_ATTRIBUTE, value != null ? value : "");
                }
            }
        }
        return parentNode;
    }

    private static Element addChild(Element parent, String childName) {
        Element child = new Element(childName);
        parent.addContent(child);
        return child;
    }

    @Override
    public void loadState(Element parentNode) {
        myBackendCompilerCache = findCompiler(parentNode.getAttributeValue("compiler"));
        myAddNotNullAssertions = Boolean.parseBoolean(parentNode.getAttributeValue(ADD_NOTNULL_ASSERTIONS, String.valueOf(Boolean.FALSE)));

        Element annotationProcessingSettings = parentNode.getChild(ANNOTATION_PROCESSING);
        if (annotationProcessingSettings != null) {
            List profiles = annotationProcessingSettings.getChildren("profile");
            if (!profiles.isEmpty()) {
                for (Object elem : profiles) {
                    Element profileElement = (Element)elem;
                    boolean isDefault = "true".equals(profileElement.getAttributeValue("default"));
                    if (isDefault) {
                        AnnotationProcessorProfileSerializer.readExternal(myDefaultProcessorsProfile, profileElement);
                    }
                    else {
                        ProcessorConfigProfile profile = new ProcessorConfigProfileImpl("");
                        AnnotationProcessorProfileSerializer.readExternal(profile, profileElement);
                        myModuleProcessorProfiles.add(profile);
                    }
                }
            }
            else {
                // assuming older format
                loadProfilesFromOldFormat(annotationProcessingSettings);
            }
        }

        myBytecodeTargetLevel = null;
        myModuleBytecodeTarget.clear();
        Element bytecodeTargetElement = parentNode.getChild(BYTECODE_TARGET_LEVEL);
        if (bytecodeTargetElement != null) {
            myBytecodeTargetLevel = bytecodeTargetElement.getAttributeValue(TARGET_ATTRIBUTE);
            for (Element elem : bytecodeTargetElement.getChildren(MODULE)) {
                String name = elem.getAttributeValue(NAME);
                if (name == null) {
                    continue;
                }
                String target = elem.getAttributeValue(TARGET_ATTRIBUTE);
                if (target == null) {
                    continue;
                }
                myModuleBytecodeTarget.put(name, target);
            }
        }

    }

    private void loadProfilesFromOldFormat(Element processing) {
        // collect data
        boolean isEnabled = Boolean.parseBoolean(processing.getAttributeValue(ENABLED, "false"));
        boolean isUseClasspath = Boolean.parseBoolean(processing.getAttributeValue("useClasspath", "true"));
        StringBuilder processorPath = new StringBuilder();
        Set<String> optionPairs = new HashSet<>();
        Set<String> processors = new HashSet<>();
        List<Pair<String, String>> modulesToProcess = new ArrayList<>();

        for (Object child : processing.getChildren("processorPath")) {
            Element pathElement = (Element)child;
            String path = pathElement.getAttributeValue("value", (String)null);
            if (path != null) {
                if (processorPath.length() > 0) {
                    processorPath.append(File.pathSeparator);
                }
                processorPath.append(path);
            }
        }

        for (Object child : processing.getChildren("processor")) {
            Element processorElement = (Element)child;
            String proc = processorElement.getAttributeValue(NAME, (String)null);
            if (proc != null) {
                processors.add(proc);
            }
            StringTokenizer tokenizer = new StringTokenizer(processorElement.getAttributeValue("options", ""), " ", false);
            while (tokenizer.hasMoreTokens()) {
                String pair = tokenizer.nextToken();
                optionPairs.add(pair);
            }
        }

        for (Object child : processing.getChildren("processModule")) {
            Element moduleElement = (Element)child;
            String name = moduleElement.getAttributeValue(NAME, (String)null);
            if (name == null) {
                continue;
            }
            String dir = moduleElement.getAttributeValue("generatedDirName", (String)null);
            modulesToProcess.add(Couple.of(name, dir));
        }

        myDefaultProcessorsProfile.setEnabled(false);
        myDefaultProcessorsProfile.setObtainProcessorsFromClasspath(isUseClasspath);
        if (processorPath.length() > 0) {
            myDefaultProcessorsProfile.setProcessorPath(processorPath.toString());
        }
        if (!optionPairs.isEmpty()) {
            for (String pair : optionPairs) {
                int index = pair.indexOf("=");
                if (index > 0) {
                    myDefaultProcessorsProfile.setOption(pair.substring(0, index), pair.substring(index + 1));
                }
            }
        }
        for (String processor : processors) {
            myDefaultProcessorsProfile.addProcessor(processor);
        }

        Map<String, Set<String>> dirNameToModulesMap = new HashMap<>();
        for (Pair<String, String> moduleDirPair : modulesToProcess) {
            String dir = moduleDirPair.getSecond();
            Set<String> set = dirNameToModulesMap.get(dir);
            if (set == null) {
                set = new HashSet<>();
                dirNameToModulesMap.put(dir, set);
            }
            set.add(moduleDirPair.getFirst());
        }

        int profileIndex = 0;
        for (Map.Entry<String, Set<String>> entry : dirNameToModulesMap.entrySet()) {
            String dirName = entry.getKey();
            ProcessorConfigProfile profile = new ProcessorConfigProfileImpl(myDefaultProcessorsProfile);
            profile.setName("Profile" + (++profileIndex));
            profile.setEnabled(isEnabled);
            profile.setGeneratedSourcesDirectoryName(dirName, false);
            for (String moduleName : entry.getValue()) {
                profile.addModuleName(moduleName);
            }
            myModuleProcessorProfiles.add(profile);
        }
    }

    @Nonnull
    @RequiredReadAction
    public ProcessorConfigProfile getAnnotationProcessingConfiguration(Module module) {
        Map<Module, ProcessorConfigProfile> map = myProcessorsProfilesMap;
        if (map == null) {
            map = new HashMap<>();
            Map<String, Module> namesMap = new HashMap<>();
            for (Module m : ModuleManager.getInstance(module.getProject()).getModules()) {
                namesMap.put(m.getName(), m);
            }
            if (!namesMap.isEmpty()) {
                for (ProcessorConfigProfile profile : myModuleProcessorProfiles) {
                    for (String name : profile.getModuleNames()) {
                        Module mod = namesMap.get(name);
                        if (mod != null) {
                            map.put(mod, profile);
                        }
                    }
                }
            }
            myProcessorsProfilesMap = map;
        }
        ProcessorConfigProfile profile = map.get(module);
        return profile != null ? profile : myDefaultProcessorsProfile;
    }

    @Nonnull
    public ProcessorConfigProfile getDefaultProcessorProfile() {
        return myDefaultProcessorsProfile;
    }

    @Nonnull
    public List<ProcessorConfigProfile> getModuleProcessorProfiles() {
        return myModuleProcessorProfiles;
    }

    public void setDefaultProcessorProfile(ProcessorConfigProfile profile) {
        myDefaultProcessorsProfile.initFrom(profile);
    }

    public void setModuleProcessorProfiles(Collection<ProcessorConfigProfile> moduleProfiles) {
        myModuleProcessorProfiles.clear();
        myModuleProcessorProfiles.addAll(moduleProfiles);
        myProcessorsProfilesMap = null;
    }

    public boolean isAnnotationProcessorsEnabled() {
        if (myDefaultProcessorsProfile.isEnabled()) {
            return true;
        }
        for (ProcessorConfigProfile profile : myModuleProcessorProfiles) {
            if (profile.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    public void removeModuleProcessorProfile(ProcessorConfigProfile profile) {
        myModuleProcessorProfiles.remove(profile);
        myProcessorsProfilesMap = null; // clear cache
    }

    public void addModuleProcessorProfile(@Nonnull ProcessorConfigProfile profile) {
        myModuleProcessorProfiles.add(profile);
        myProcessorsProfilesMap = null; // clear cache
    }

    @Nullable
    public ProcessorConfigProfile findModuleProcessorProfile(@Nonnull String name) {
        for (ProcessorConfigProfile profile : myModuleProcessorProfiles) {
            if (name.equals(profile.getName())) {
                return profile;
            }
        }

        return null;
    }

    public void setAddNotNullAssertions(boolean addNotNullAssertions) {
        myAddNotNullAssertions = addNotNullAssertions;
    }

    public boolean isAddNotNullAssertions() {
        return myAddNotNullAssertions;
    }

    @Deprecated
    public void setModulesBytecodeTargetMap(@Nonnull Map<String, String> mapping) {
        myModuleBytecodeTarget.clear();
        myModuleBytecodeTarget.putAll(mapping);
    }

    @Deprecated
    public Map<String, String> getModulesBytecodeTargetMap() {
        return myModuleBytecodeTarget;
    }

    public void setProjectBytecodeTarget(@Nullable String level) {
        myBytecodeTargetLevel = level;
    }

    @Nullable
    public String getProjectBytecodeTarget() {
        return myBytecodeTargetLevel;
    }
}
