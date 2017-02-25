package com.intellij.compiler.impl.javaCompiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.compiler.impl.javaCompiler.annotationProcessing.ProcessorConfigProfile;
import com.intellij.compiler.impl.javaCompiler.annotationProcessing.impl.ProcessorConfigProfileImpl;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import consulo.annotations.DeprecationInfo;
import consulo.annotations.RequiredReadAction;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerEP;
import consulo.java.module.extension.JavaModuleExtension;

@State(
		name = "JavaCompilerConfiguration",
		storages = {
				@Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/compiler.xml")
		})
public class JavaCompilerConfiguration implements PersistentStateComponent<Element>
{
	@NotNull
	public static JavaCompilerConfiguration getInstance(@NotNull Project project)
	{
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

	@NotNull
	private final Project myProject;

	private BackendCompiler myBackendCompilerCache;

	private final ProcessorConfigProfile myDefaultProcessorsProfile = new ProcessorConfigProfileImpl("Default");
	private final List<ProcessorConfigProfile> myModuleProcessorProfiles = new ArrayList<ProcessorConfigProfile>();

	// the map is calculated by module processor profiles list for faster access to module settings
	private Map<Module, ProcessorConfigProfile> myProcessorsProfilesMap = null;
	private boolean myAddNotNullAssertions;

	@Nullable
	private String myBytecodeTargetLevel = null;  // null means compiler default

	@Deprecated
	@DeprecationInfo("We used JavaModuleExtension for store info about bytecode version")
	private final Map<String, String> myModuleBytecodeTarget = new HashMap<String, String>();

	public JavaCompilerConfiguration(@NotNull Project project)
	{
		myProject = project;
	}

	@Nullable
	@RequiredReadAction
	public String getBytecodeTargetLevel(@NotNull Module module)
	{
		String level = myModuleBytecodeTarget.get(module.getName());
		if(level != null)
		{
			return level.isEmpty() ? null : level;
		}

		JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
		if(extension != null)
		{
			String bytecodeVersion = extension.getBytecodeVersion();
			if(bytecodeVersion != null)
			{
				return StringUtil.nullize(bytecodeVersion);
			}
		}
		return myBytecodeTargetLevel;
	}

	@NotNull
	public BackendCompiler getActiveCompiler()
	{
		if(myBackendCompilerCache == null)
		{
			myBackendCompilerCache = findCompiler(DEFAULT_COMPILER);
		}
		return myBackendCompilerCache;
	}

	public void setActiveCompiler(@NotNull BackendCompiler key)
	{
		myBackendCompilerCache = key;
	}

	@Nullable
	public BackendCompiler findCompiler(@NotNull String className)
	{
		for(BackendCompilerEP ep : BackendCompiler.EP_NAME.getExtensions(myProject))
		{
			if(className.equals(ep.getInstance(myProject).getClass().getSimpleName()))
			{
				return ep.getInstance(myProject);
			}
		}
		return null;
	}

	@Nullable
	@Override
	public Element getState()
	{
		Element parentNode = new Element("state");

		final Element annotationProcessingSettings = addChild(parentNode, ANNOTATION_PROCESSING);
		final Element defaultProfileElem = addChild(annotationProcessingSettings, "profile").setAttribute("default", "true");
		AnnotationProcessorProfileSerializer.writeExternal(myDefaultProcessorsProfile, defaultProfileElem);
		for(ProcessorConfigProfile profile : myModuleProcessorProfiles)
		{
			final Element profileElem = addChild(annotationProcessingSettings, "profile").setAttribute("default", "false");
			AnnotationProcessorProfileSerializer.writeExternal(profile, profileElem);
		}

		parentNode.setAttribute("compiler", myBackendCompilerCache == null ? DEFAULT_COMPILER : myBackendCompilerCache.getClass().getSimpleName());
		if(myAddNotNullAssertions)
		{
			parentNode.setAttribute(ADD_NOTNULL_ASSERTIONS, String.valueOf(Boolean.TRUE));
		}

		if(!StringUtil.isEmpty(myBytecodeTargetLevel) || !myModuleBytecodeTarget.isEmpty())
		{
			final Element bytecodeTarget = addChild(parentNode, BYTECODE_TARGET_LEVEL);
			if(!StringUtil.isEmpty(myBytecodeTargetLevel))
			{
				bytecodeTarget.setAttribute(TARGET_ATTRIBUTE, myBytecodeTargetLevel);
			}
			if(!myModuleBytecodeTarget.isEmpty())
			{
				final List<String> moduleNames = new ArrayList<String>(myModuleBytecodeTarget.keySet());
				Collections.sort(moduleNames, String.CASE_INSENSITIVE_ORDER);
				for(String name : moduleNames)
				{
					final Element moduleElement = addChild(bytecodeTarget, MODULE);
					moduleElement.setAttribute(NAME, name);
					final String value = myModuleBytecodeTarget.get(name);
					moduleElement.setAttribute(TARGET_ATTRIBUTE, value != null ? value : "");
				}
			}
		}
		return parentNode;
	}

	private static Element addChild(Element parent, final String childName)
	{
		final Element child = new Element(childName);
		parent.addContent(child);
		return child;
	}

	@Override
	public void loadState(Element parentNode)
	{
		myBackendCompilerCache = findCompiler(parentNode.getAttributeValue("compiler"));
		myAddNotNullAssertions = Boolean.parseBoolean(parentNode.getAttributeValue(ADD_NOTNULL_ASSERTIONS, String.valueOf(Boolean.FALSE)));

		final Element annotationProcessingSettings = parentNode.getChild(ANNOTATION_PROCESSING);
		if(annotationProcessingSettings != null)
		{
			final List profiles = annotationProcessingSettings.getChildren("profile");
			if(!profiles.isEmpty())
			{
				for(Object elem : profiles)
				{
					final Element profileElement = (Element) elem;
					final boolean isDefault = "true".equals(profileElement.getAttributeValue("default"));
					if(isDefault)
					{
						AnnotationProcessorProfileSerializer.readExternal(myDefaultProcessorsProfile, profileElement);
					}
					else
					{
						final ProcessorConfigProfile profile = new ProcessorConfigProfileImpl("");
						AnnotationProcessorProfileSerializer.readExternal(profile, profileElement);
						myModuleProcessorProfiles.add(profile);
					}
				}
			}
			else
			{
				// assuming older format
				loadProfilesFromOldFormat(annotationProcessingSettings);
			}
		}

		myBytecodeTargetLevel = null;
		myModuleBytecodeTarget.clear();
		Element bytecodeTargetElement = parentNode.getChild(BYTECODE_TARGET_LEVEL);
		if(bytecodeTargetElement != null)
		{
			myBytecodeTargetLevel = bytecodeTargetElement.getAttributeValue(TARGET_ATTRIBUTE);
			for(Element elem : bytecodeTargetElement.getChildren(MODULE))
			{
				String name = elem.getAttributeValue(NAME);
				if(name == null)
				{
					continue;
				}
				String target = elem.getAttributeValue(TARGET_ATTRIBUTE);
				if(target == null)
				{
					continue;
				}
				myModuleBytecodeTarget.put(name, target);
			}
		}

	}

	private void loadProfilesFromOldFormat(Element processing)
	{
		// collect data
		final boolean isEnabled = Boolean.parseBoolean(processing.getAttributeValue(ENABLED, "false"));
		final boolean isUseClasspath = Boolean.parseBoolean(processing.getAttributeValue("useClasspath", "true"));
		final StringBuilder processorPath = new StringBuilder();
		final Set<String> optionPairs = new HashSet<String>();
		final Set<String> processors = new HashSet<String>();
		final List<Pair<String, String>> modulesToProcess = new ArrayList<Pair<String, String>>();

		for(Object child : processing.getChildren("processorPath"))
		{
			final Element pathElement = (Element) child;
			final String path = pathElement.getAttributeValue("value", (String) null);
			if(path != null)
			{
				if(processorPath.length() > 0)
				{
					processorPath.append(File.pathSeparator);
				}
				processorPath.append(path);
			}
		}

		for(Object child : processing.getChildren("processor"))
		{
			final Element processorElement = (Element) child;
			final String proc = processorElement.getAttributeValue(NAME, (String) null);
			if(proc != null)
			{
				processors.add(proc);
			}
			final StringTokenizer tokenizer = new StringTokenizer(processorElement.getAttributeValue("options", ""), " ", false);
			while(tokenizer.hasMoreTokens())
			{
				final String pair = tokenizer.nextToken();
				optionPairs.add(pair);
			}
		}

		for(Object child : processing.getChildren("processModule"))
		{
			final Element moduleElement = (Element) child;
			final String name = moduleElement.getAttributeValue(NAME, (String) null);
			if(name == null)
			{
				continue;
			}
			final String dir = moduleElement.getAttributeValue("generatedDirName", (String) null);
			modulesToProcess.add(Pair.create(name, dir));
		}

		myDefaultProcessorsProfile.setEnabled(false);
		myDefaultProcessorsProfile.setObtainProcessorsFromClasspath(isUseClasspath);
		if(processorPath.length() > 0)
		{
			myDefaultProcessorsProfile.setProcessorPath(processorPath.toString());
		}
		if(!optionPairs.isEmpty())
		{
			for(String pair : optionPairs)
			{
				final int index = pair.indexOf("=");
				if(index > 0)
				{
					myDefaultProcessorsProfile.setOption(pair.substring(0, index), pair.substring(index + 1));
				}
			}
		}
		for(String processor : processors)
		{
			myDefaultProcessorsProfile.addProcessor(processor);
		}

		final Map<String, Set<String>> dirNameToModulesMap = new HashMap<String, Set<String>>();
		for(Pair<String, String> moduleDirPair : modulesToProcess)
		{
			final String dir = moduleDirPair.getSecond();
			Set<String> set = dirNameToModulesMap.get(dir);
			if(set == null)
			{
				set = new HashSet<String>();
				dirNameToModulesMap.put(dir, set);
			}
			set.add(moduleDirPair.getFirst());
		}

		int profileIndex = 0;
		for(Map.Entry<String, Set<String>> entry : dirNameToModulesMap.entrySet())
		{
			final String dirName = entry.getKey();
			final ProcessorConfigProfile profile = new ProcessorConfigProfileImpl(myDefaultProcessorsProfile);
			profile.setName("Profile" + (++profileIndex));
			profile.setEnabled(isEnabled);
			profile.setGeneratedSourcesDirectoryName(dirName, false);
			for(String moduleName : entry.getValue())
			{
				profile.addModuleName(moduleName);
			}
			myModuleProcessorProfiles.add(profile);
		}
	}

	@NotNull
	public ProcessorConfigProfile getAnnotationProcessingConfiguration(Module module)
	{
		Map<Module, ProcessorConfigProfile> map = myProcessorsProfilesMap;
		if(map == null)
		{
			map = new HashMap<Module, ProcessorConfigProfile>();
			final Map<String, Module> namesMap = new HashMap<String, Module>();
			for(Module m : ModuleManager.getInstance(module.getProject()).getModules())
			{
				namesMap.put(m.getName(), m);
			}
			if(!namesMap.isEmpty())
			{
				for(ProcessorConfigProfile profile : myModuleProcessorProfiles)
				{
					for(String name : profile.getModuleNames())
					{
						final Module mod = namesMap.get(name);
						if(mod != null)
						{
							map.put(mod, profile);
						}
					}
				}
			}
			myProcessorsProfilesMap = map;
		}
		final ProcessorConfigProfile profile = map.get(module);
		return profile != null ? profile : myDefaultProcessorsProfile;
	}

	@NotNull
	public ProcessorConfigProfile getDefaultProcessorProfile()
	{
		return myDefaultProcessorsProfile;
	}

	@NotNull
	public List<ProcessorConfigProfile> getModuleProcessorProfiles()
	{
		return myModuleProcessorProfiles;
	}

	public void setDefaultProcessorProfile(ProcessorConfigProfile profile)
	{
		myDefaultProcessorsProfile.initFrom(profile);
	}

	public void setModuleProcessorProfiles(Collection<ProcessorConfigProfile> moduleProfiles)
	{
		myModuleProcessorProfiles.clear();
		myModuleProcessorProfiles.addAll(moduleProfiles);
		myProcessorsProfilesMap = null;
	}

	public boolean isAnnotationProcessorsEnabled()
	{
		if(myDefaultProcessorsProfile.isEnabled())
		{
			return true;
		}
		for(ProcessorConfigProfile profile : myModuleProcessorProfiles)
		{
			if(profile.isEnabled())
			{
				return true;
			}
		}
		return false;
	}

	public void removeModuleProcessorProfile(ProcessorConfigProfile profile)
	{
		myModuleProcessorProfiles.remove(profile);
		myProcessorsProfilesMap = null; // clear cache
	}

	public void addModuleProcessorProfile(@NotNull ProcessorConfigProfile profile)
	{
		myModuleProcessorProfiles.add(profile);
		myProcessorsProfilesMap = null; // clear cache
	}

	@Nullable
	public ProcessorConfigProfile findModuleProcessorProfile(@NotNull String name)
	{
		for(ProcessorConfigProfile profile : myModuleProcessorProfiles)
		{
			if(name.equals(profile.getName()))
			{
				return profile;
			}
		}

		return null;
	}

	public void setAddNotNullAssertions(boolean addNotNullAssertions)
	{
		myAddNotNullAssertions = addNotNullAssertions;
	}

	public boolean isAddNotNullAssertions()
	{
		return myAddNotNullAssertions;
	}

	@Deprecated
	public void setModulesBytecodeTargetMap(@NotNull Map<String, String> mapping)
	{
		myModuleBytecodeTarget.clear();
		myModuleBytecodeTarget.putAll(mapping);
	}

	@Deprecated
	public Map<String, String> getModulesBytecodeTargetMap()
	{
		return myModuleBytecodeTarget;
	}

	public void setProjectBytecodeTarget(@Nullable String level)
	{
		myBytecodeTargetLevel = level;
	}

	@Nullable
	public String getProjectBytecodeTarget()
	{
		return myBytecodeTargetLevel;
	}
}
