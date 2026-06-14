// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.indexing.impl.stubs.index.JavaModuleNameIndex;
import com.intellij.java.indexing.search.searches.JavaModuleSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.PsiJavaModuleModificationTracker;
import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.java.language.impl.psi.util.JavaManifestUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValueProvider.Result;
import consulo.application.util.CachedValuesManager;
import consulo.component.util.graph.DFSTBuilder;
import consulo.component.util.graph.Graph;
import consulo.component.util.graph.GraphGenerator;
import consulo.content.ContentFolderTypeProvider;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.language.psi.stub.DumbModeAccessType;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.project.DumbService;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.project.content.ProjectRootModificationTracker;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.collection.SmartList;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Trinity;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.jar.JarFile;

import static consulo.util.lang.ObjectUtil.tryCast;

public final class JavaModuleGraphUtil {
  private JavaModuleGraphUtil() {
  }

  @Nullable
  public static PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    if (element instanceof PsiJavaModule module) {
      return module;
    }
    if (element.getContainingFile() instanceof PsiJavaFile file) {
      PsiJavaModule module = file.getModuleDeclaration();
      if (module != null) {
        return module;
      }
    }

    if (element instanceof PsiFileSystemItem fsItem) {
      return findDescriptorByFile(fsItem.getVirtualFile(), fsItem.getProject());
    }

    PsiFile file = element.getContainingFile();
    if (file != null) {
      return findDescriptorByFile(file.getVirtualFile(), file.getProject());
    }

    if (element instanceof PsiJavaPackage psiPackage) {
      PsiDirectory[] directories = psiPackage.getDirectories((GlobalSearchScope) ProjectScopes.getLibrariesScope(psiPackage.getProject()));
      for (PsiDirectory directory : directories) {
        PsiJavaModule descriptor = findDescriptorByFile(directory.getVirtualFile(), directory.getProject());
        if (descriptor != null) {
          return descriptor;
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiJavaModule findDescriptorByFile(@Nullable VirtualFile file, Project project) {
    if (file == null) {
      return null;
    }
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    return index.isInLibrary(file)
      ? findDescriptorInLibrary(file, project)
      : findDescriptorByModule(index.getModuleForFile(file), index.isInTestSourceContent(file));
  }

  /**
   * @param file    library content root
   * @param project current project
   * @return JPMS module declared by the supplied library; null if not found
   */
  @Nullable
  @RequiredReadAction
  public static PsiJavaModule findDescriptorInLibrary(VirtualFile file, Project project) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    VirtualFile root = index.getClassRootForFile(file);
    if (root != null) {
      VirtualFile descriptorFile = JavaModuleNameIndex.descriptorFile(root);
      if (descriptorFile != null) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile) psiFile).getModuleDeclaration();
        }
      }
      else if (root.getFileSystem() instanceof ArchiveFileSystem && "jar".equalsIgnoreCase(root.getExtension())) {
        return LightJavaModule.findModule(PsiManager.getInstance(project), root);
      }
    }
    else {
      root = index.getSourceRootForFile(file);
      if (root != null) {
        VirtualFile moduleDescriptor = root.findChild(PsiJavaModule.MODULE_INFO_FILE);
        PsiFile psiFile = moduleDescriptor != null ? PsiManager.getInstance(project).findFile(moduleDescriptor) : null;
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile) psiFile).getModuleDeclaration();
        }
      }
    }
    return null;
  }

  @Nullable
  public static PsiJavaModule findDescriptorByModule(@Nullable Module module, boolean inTests) {
    if (module == null) {
      return null;
    }
    CachedValuesManager valuesManager = CachedValuesManager.getManager(module.getProject());
    PsiJavaModule javaModule = inTests //to have different providers for production and tests
        ? valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, true))
        : valuesManager.getCachedValue(module, () -> createModuleCacheResult(module, false));
    return javaModule != null && javaModule.isValid() ? javaModule : null;
  }

  private static Result<PsiJavaModule> createModuleCacheResult(Module module, boolean inTests) {
    Project project = module.getProject();
    return Result.create(findDescriptionByModuleInner(module, inTests),
        ProjectRootModificationTracker.getInstance(project),
        PsiJavaModuleModificationTracker.getInstance(project));
  }

  @Nullable
  private static PsiJavaModule findDescriptionByModuleInner(Module module, boolean inTests) {
    Project project = module.getProject();
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);
    String virtualAutoModuleName = JavaManifestUtil.getManifestAttributeValue(module, PsiJavaModule.AUTO_MODULE_NAME);
    if (!DumbService.isDumb(project) &&
        FilenameIndex.getVirtualFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, moduleScope).isEmpty() &&
        FilenameIndex.getVirtualFilesByName(project, "MANIFEST.MF", moduleScope).isEmpty() &&
        virtualAutoModuleName == null) {
      return null;
    }
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    Set<VirtualFile> excludeRoots = new HashSet<>(Arrays.asList(rootManager.getExcludeRoots()));
    Predicate<ContentFolderTypeProvider> sourceScope = inTests ? LanguageContentFolderScopes.onlyTest() : LanguageContentFolderScopes.onlyProduction();
    List<VirtualFile> sourceRoots =
        ContainerUtil.filter(Arrays.asList(rootManager.getContentFolderFiles(sourceScope)), root -> !excludeRoots.contains(root));

    List<VirtualFile> files = ContainerUtil.mapNotNull(sourceRoots, root -> root.findChild(PsiJavaModule.MODULE_INFO_FILE));
    if (files.isEmpty()) {
      // META-INF/MANIFEST.MF can live in source or resource roots
      Predicate<ContentFolderTypeProvider> sourceAndResourceScope =
          inTests ? LanguageContentFolderScopes.test() : LanguageContentFolderScopes.production();
      List<VirtualFile> roots =
          ContainerUtil.filter(Arrays.asList(rootManager.getContentFolderFiles(sourceAndResourceScope)), root -> !excludeRoots.contains(root));
      files = ContainerUtil.mapNotNull(roots, root -> root.findFileByRelativePath(JarFile.MANIFEST_NAME));
      if (files.size() == 1 || new HashSet<>(files).size() == 1) {
        VirtualFile manifest = files.get(0);
        PsiFile manifestPsi = PsiManager.getInstance(project).findFile(manifest);
        if (manifestPsi != null) {
          return LanguageCachedValueUtil.getCachedValue(manifestPsi, () -> {
            String name = LightJavaModule.claimedModuleName(manifest);
            LightJavaModule result =
                name != null ? LightJavaModule.create(PsiManager.getInstance(project), manifest.getParent().getParent(), name) : null;
            return Result.create(result, manifestPsi, ProjectRootModificationTracker.getInstance(project));
          });
        }
      }
      // automatic module name provided by the build system (e.g. maven-jar-plugin Automatic-Module-Name in the POM)
      VirtualFile[] sourceSourceRoots = rootManager.getContentFolderFiles(LanguageContentFolderScopes.onlyProduction());
      if (virtualAutoModuleName != null && sourceSourceRoots.length != 0) {
        return LightJavaModule.create(PsiManager.getInstance(project), sourceSourceRoots[0], virtualAutoModuleName);
      }
    }
    else {
      VirtualFile file = files.get(0);
      if (ContainerUtil.and(files, f -> f.equals(file))) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile instanceof PsiJavaFile) {
          return ((PsiJavaFile) psiFile).getModuleDeclaration();
        }
      }
    }

    return null;
  }

  public static Collection<PsiJavaModule> findCycle(PsiJavaModule module) {
    Project project = module.getProject();
    List<Set<PsiJavaModule>> cycles = CachedValuesManager.getManager(project).getCachedValue(project, () ->
        Result.create(findCycles(project),
                      PsiJavaModuleModificationTracker.getInstance(project),
                      ProjectRootModificationTracker.getInstance(project)));
    return ObjectUtil.notNull(ContainerUtil.find(cycles, set -> set.contains(module)), Collections.emptyList());
  }

  public static boolean exports(PsiJavaModule source, String packageName, @Nullable PsiJavaModule target) {
    Map<String, Set<String>> exports = LanguageCachedValueUtil.getCachedValue(source, () ->
        Result.create(exportsMap(source), source.getContainingFile()));
    Set<String> targets = exports.get(packageName);
    return targets != null && (targets.isEmpty() || target != null && targets.contains(target.getName()));
  }

  public static boolean reads(PsiJavaModule source, PsiJavaModule destination) {
    return getRequiresGraph(source).reads(source, destination);
  }

  public static Set<PsiJavaModule> getAllDependencies(PsiJavaModule source) {
    return getRequiresGraph(source).getAllDependencies(source);
  }

  /**
   * Retrieves a list of package accessibility statements for a given Java module that
   * are accessible to the specified place.
   *
   * @param place the place from which accessibility is being checked.
   * @param module the module whose exported packages are to be retrieved.
   * @return a list of `PsiPackageAccessibilityStatement` elements that represent the exported packages accessible
   *         to the specified place.
   */
  public static List<PsiPackageAccessibilityStatement> getExportedPackages(PsiElement place, PsiJavaModule module) {
    List<PsiPackageAccessibilityStatement> results = new ArrayList<>();
    PsiJavaModule currentModule = findDescriptorByElement(place);
    List<PsiPackageAccessibilityStatement> exports = getAllDeclaredExports(module);
    for (PsiPackageAccessibilityStatement export : exports) {
      PsiJavaCodeReferenceElement aPackage = export.getPackageReference();
      if (aPackage == null) continue;
      List<String> accessibleModules = export.getModuleNames();
      if (!accessibleModules.isEmpty()) {
        if (currentModule == null || !accessibleModules.contains(currentModule.getName())) continue;
      }
      results.add(export);
    }
    return results;
  }

  /**
   * Retrieves all transitive modules required by the given module, including the module itself.
   *
   * @param module the module for which transitive dependencies are being collected; must not be null
   * @return a set of transitive modules required by the given module, including the module itself
   */
  public static Set<PsiJavaModule> getAllTransitiveModulesIncludeCurrent(PsiJavaModule module) {
    return LanguageCachedValueUtil.getCachedValue(module, () -> {
      Project project = module.getProject();
      Set<PsiJavaModule> collected = new HashSet<>();
      collected.addAll(getAllDependencies(module));
      collected.add(module);
      return Result.create(collected,
                           PsiJavaModuleModificationTracker.getInstance(project),
                           ProjectRootModificationTracker.getInstance(project));
    });
  }

  private static List<PsiPackageAccessibilityStatement> getAllDeclaredExports(PsiJavaModule module) {
    Project project = module.getProject();
    return LanguageCachedValueUtil.getCachedValue(module, () -> {
      List<PsiPackageAccessibilityStatement> exports = new ArrayList<>();
      for (PsiJavaModule javaModule : getAllTransitiveModulesIncludeCurrent(module)) {
        for (PsiPackageAccessibilityStatement export : javaModule.getExports()) {
          exports.add(export);
        }
      }
      return Result.create(exports,
                           PsiJavaModuleModificationTracker.getInstance(project),
                           ProjectRootModificationTracker.getInstance(project));
    });
  }

  @Nullable
  public static Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(PsiJavaModule module) {
    return getRequiresGraph(module).findConflict(module);
  }

  public static
  @Nullable
  PsiJavaModule findOrigin(PsiJavaModule module, String packageName) {
    return getRequiresGraph(module).findOrigin(module, packageName);
  }

  /*
   * Looks for cycles between Java modules in the project sources.
   * Library/JDK modules are excluded in an assumption there can't be any lib -> src dependencies.
   * Module references are resolved "globally" (i.e., without taking project dependencies into account).
   */
  private static List<Set<PsiJavaModule>> findCycles(Project project) {
    Set<PsiJavaModule> projectModules = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      List<PsiJavaModule> descriptors = ContainerUtil.mapNotNull(ModuleRootManager.getInstance(module).getSourceRoots(true),
          root -> findDescriptorByFile(root, project));
      if (descriptors.size() > 1) {
        return Collections.emptyList();  // aborts the process when there are incorrect modules in the project
      }
      if (descriptors.size() == 1) {
        projectModules.add(descriptors.get(0));
      }
    }

    if (!projectModules.isEmpty()) {
      MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
      for (PsiJavaModule module : projectModules) {
        for (PsiRequiresStatement statement : module.getRequires()) {
          PsiJavaModuleReference ref = statement.getModuleReference();
          if (ref != null) {
            ResolveResult[] results = ref.multiResolve(true);
            if (results.length == 1) {
              PsiJavaModule dependency = (PsiJavaModule) results[0].getElement();
              if (dependency != null && projectModules.contains(dependency)) {
                relations.putValue(module, dependency);
              }
            }
          }
        }
      }

      if (!relations.isEmpty()) {
        Graph<PsiJavaModule> graph = new ChameleonGraph<>(relations, false);
        DFSTBuilder<PsiJavaModule> builder = new DFSTBuilder<>(graph);
        Collection<Collection<PsiJavaModule>> components = builder.getComponents();
        if (!components.isEmpty()) {
          return ContainerUtil.map(components, elements -> new LinkedHashSet<>(elements));
        }
      }
    }

    return Collections.emptyList();
  }

  private static Map<String, Set<String>> exportsMap(PsiJavaModule source) {
    Map<String, Set<String>> map = new HashMap<>();
    for (PsiPackageAccessibilityStatement statement : source.getExports()) {
      String pkg = statement.getPackageName();
      List<String> targets = statement.getModuleNames();
      map.put(pkg, targets.isEmpty() ? Collections.emptySet() : new HashSet<>(targets));
    }
    return map;
  }

  private static RequiresGraph getRequiresGraph(PsiJavaModule module) {
    Project project = module.getProject();
    if (DumbService.getInstance(project).isAlternativeResolveEnabled()) {
      return FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> buildRequiresGraph(project));
    }
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
        Result.create(FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> buildRequiresGraph(project)),
                      PsiJavaModuleModificationTracker.getInstance(project),
                      ProjectRootModificationTracker.getInstance(project)));
  }

  /*
   * Collects all module dependencies in the project.
   * The resulting graph is used for tracing readability and checking package conflicts.
   */
  private static RequiresGraph buildRequiresGraph(Project project) {
    MultiMap<String, PsiJavaModule> allModules = MultiMap.create();
    MultiMap<PsiJavaModule, PsiJavaModule> relations = MultiMap.create();
    Set<String> transitiveEdges = new HashSet<>();

    GlobalSearchScope scope = (GlobalSearchScope) ProjectScopes.getAllScope(project);
    JavaModuleSearch.allModules(project, scope).forEach(module -> {
      allModules.putValue(module.getName(), module);
      return true;
    });

    for (PsiJavaModule module : allModules.values()) {
      if (!(module instanceof LightJavaModule)) {
        visit(module, relations, transitiveEdges, allModules);
      }
    }

    Graph<PsiJavaModule> graph = GraphGenerator.generate(new ChameleonGraph<>(relations, true));
    return new RequiresGraph(graph, transitiveEdges);
  }

  /**
   * Visits a given module and processes its dependencies and relations. Updates the set of visited modules,
   * the relations between modules, and the set of transitive edges based on the requires statements within
   * the module.
   *
   * @param module          the module to be visited
   * @param relations       a mapping that represents module dependencies
   * @param transitiveEdges a set of transitive edges representing transitive dependencies
   * @param allModules      a map of module names to PsiJavaModule instances
   */
  private static void visit(PsiJavaModule module,
                            MultiMap<PsiJavaModule, PsiJavaModule> relations,
                            Set<String> transitiveEdges,
                            MultiMap<String, PsiJavaModule> allModules) {
    relations.putValues(module, Collections.emptyList());
    boolean explicitJavaBase = false;

    GlobalSearchScope scope = GlobalSearchScope.allScope(module.getProject());
    for (PsiRequiresStatement statement : module.getRequires()) {
      PsiJavaModuleReference ref = statement.getModuleReference();
      if (ref != null) {
        String moduleName = ref.getCanonicalText();
        if (PsiJavaModule.JAVA_BASE.equals(moduleName)) {
          explicitJavaBase = true;
        }
        for (PsiJavaModule dependency : filterModules(allModules.get(moduleName), scope)) {
          relations.putValue(module, dependency);
          if (statement.hasModifierProperty(PsiModifier.TRANSITIVE)) {
            transitiveEdges.add(RequiresGraph.key(dependency, module));
          }
        }
      }
    }

    if (!explicitJavaBase) {
      Collection<PsiJavaModule> modules = filterModules(allModules.get(PsiJavaModule.JAVA_BASE), module.getResolveScope());
      if (modules.size() == 1) {
        relations.putValue(module, modules.iterator().next());
      }
    }
  }

  private static List<PsiJavaModule> filterModules(Collection<PsiJavaModule> modules, GlobalSearchScope scope) {
    SmartList<PsiJavaModule> filtered = new SmartList<>();
    for (PsiJavaModule candidate : modules) {
      VirtualFile candidateFile = getVirtualFile(candidate);
      if (candidateFile != null && scope.contains(candidateFile)) {
        filtered.add(candidate);
      }
    }
    return filtered;
  }

  @Nullable
  private static VirtualFile getVirtualFile(@Nullable PsiJavaModule module) {
    if (module == null) {
      return null;
    }
    if (module instanceof LightJavaModule light) {
      return light.getRootVirtualFile();
    }
    return PsiUtilCore.getVirtualFile(module);
  }

  private static final class RequiresGraph {
    private final Graph<PsiJavaModule> myGraph;
    private final Set<String> myTransitiveEdges;

    private RequiresGraph(Graph<PsiJavaModule> graph, Set<String> transitiveEdges) {
      myGraph = graph;
      myTransitiveEdges = transitiveEdges;
    }

    public boolean reads(PsiJavaModule source, PsiJavaModule destination) {
      source = getPhysicalModule(source);
      destination = getPhysicalModule(destination);
      Collection<PsiJavaModule> nodes = myGraph.getNodes();
      if (!nodes.contains(destination) || !nodes.contains(source)) {
        return false;
      }

      UniqueBuffer<PsiJavaModule> buffer = new UniqueBuffer<>();
      buffer.add(destination);
      while (!buffer.isEmpty()) {
        destination = buffer.poll();
        Iterator<PsiJavaModule> directReaders = myGraph.getOut(destination);
        while (directReaders.hasNext()) {
          PsiJavaModule next = directReaders.next();
          if (source.equals(next)) {
            return true;
          }
          if (myTransitiveEdges.contains(key(destination, next)) && !next.equals(destination)) {
            buffer.add(next);
          }
        }
      }
      return false;
    }

    public Trinity<String, PsiJavaModule, PsiJavaModule> findConflict(PsiJavaModule source) {
      source = getPhysicalModule(source);
      Map<String, PsiJavaModule> exports = new HashMap<>();
      return processExports(source, (pkg, m) -> {
        PsiJavaModule found = exports.put(pkg, m);
        return found == null ||
               found instanceof LightJavaModule && m instanceof LightJavaModule ||
               found.getName().equals(m.getName())
               ? null : new Trinity<>(pkg, found, m);
      });
    }

    public PsiJavaModule findOrigin(PsiJavaModule module, String packageName) {
      return processExports(getPhysicalModule(module), (pkg, m) -> packageName.equals(pkg) ? m : null);
    }

    private <T> T processExports(PsiJavaModule start, BiFunction<String, PsiJavaModule, T> processor) {
      start = getPhysicalModule(start);
      return myGraph.getNodes().contains(start) ? processExports(start.getName(), start, true, new HashSet<>(), processor) : null;
    }

    private <T> T processExports(String name, PsiJavaModule module, boolean direct, Set<PsiJavaModule> visited, BiFunction<String, PsiJavaModule, T> processor) {
      module = getPhysicalModule(module);
      if (visited.add(module)) {
        if (!direct) {
          for (PsiPackageAccessibilityStatement statement : module.getExports()) {
            List<String> exportTargets = statement.getModuleNames();
            if (exportTargets.isEmpty() || exportTargets.contains(name)) {
              T result = processor.apply(statement.getPackageName(), module);
              if (result != null) {
                return result;
              }
            }
          }
        }
        for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext(); ) {
          PsiJavaModule dependency = iterator.next();
          if (direct || myTransitiveEdges.contains(key(dependency, module))) {
            T result = processExports(name, dependency, false, visited, processor);
            if (result != null) {
              return result;
            }
          }
        }
      }

      return null;
    }

    public static String key(PsiJavaModule module, PsiJavaModule exporter) {
      return module.getName() + '/' + exporter.getName();
    }

    public Set<PsiJavaModule> getAllDependencies(PsiJavaModule module) {
      Set<PsiJavaModule> requires = new HashSet<>();
      collectDependencies(getPhysicalModule(module), requires);
      return requires;
    }

    private void collectDependencies(PsiJavaModule module, Set<PsiJavaModule> dependencies) {
      module = getPhysicalModule(module);
      for (Iterator<PsiJavaModule> iterator = myGraph.getIn(module); iterator.hasNext(); ) {
        PsiJavaModule dependency = iterator.next();
        if (!dependencies.contains(dependency)) {
          dependencies.add(dependency);
          collectDependencies(dependency, dependencies);
        }
      }
    }

    private static PsiJavaModule getPhysicalModule(PsiJavaModule from) {
      if (from.isPhysical()) {
        return from;
      }
      if (!(from.getContainingFile() instanceof PsiJavaFile file)) {
        return from;
      }
      if (!(file.getOriginalFile() instanceof PsiJavaFile origin)) {
        return from;
      }
      if (origin.getModuleDeclaration() instanceof PsiJavaModule result) {
        return result;
      }
      return from;
    }

    /**
     * FIFO queue that prevents duplicate additions.
     * Once added, an element cannot be added again even after being polled.
     */
    private static class UniqueBuffer<T> {
      private final Set<T> myUnique = new HashSet<>();
      private final Queue<T> myBuffer = new ArrayDeque<>();

      public void add(T value) {
        if (myUnique.add(value)) {
          myBuffer.add(value);
        }
      }

      public T poll() {
        return myBuffer.poll();
      }

      public boolean isEmpty() {
        return myBuffer.isEmpty();
      }
    }
  }

  private static final class ChameleonGraph<N> implements Graph<N> {
    private final Set<N> myNodes;
    private final MultiMap<N, N> myEdges;
    private final boolean myInbound;

    private ChameleonGraph(MultiMap<N, N> edges, boolean inbound) {
      myNodes = new HashSet<>();
      edges.entrySet().forEach(e -> {
        myNodes.add(e.getKey());
        myNodes.addAll(e.getValue());
      });
      myEdges = edges;
      myInbound = inbound;
    }

    @Override
    public Collection<N> getNodes() {
      return myNodes;
    }

    @Override
    public Iterator<N> getIn(N n) {
      return myInbound ? myEdges.get(n).iterator() : Collections.emptyIterator();
    }

    @Override
    public Iterator<N> getOut(N n) {
      return myInbound ? Collections.emptyIterator() : myEdges.get(n).iterator();
    }
  }

  public static class JavaModuleScope extends GlobalSearchScope {
    private final MultiMap<String, PsiJavaModule> myModules;
    private final boolean myIncludeLibraries;
    private final boolean myIsInTests;

    private JavaModuleScope(Project project, Set<PsiJavaModule> modules) {
      super(project);
      myModules = new MultiMap<>();
      for (PsiJavaModule module : modules) {
        myModules.putValue(module.getName(), module);
      }
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      myIncludeLibraries = ContainerUtil.or(modules, m -> {
        PsiFile containingFile = m.getContainingFile();
        if (containingFile == null) return true;
        VirtualFile moduleFile = containingFile.getVirtualFile();
        if (moduleFile == null) return true;
        return fileIndex.isInLibrary(moduleFile);
      });
      myIsInTests = !myIncludeLibraries && ContainerUtil.or(modules, m -> {
        PsiFile containingFile = m.getContainingFile();
        if (containingFile == null) return true;
        VirtualFile moduleFile = containingFile.getVirtualFile();
        if (moduleFile == null) return true;
        return fileIndex.isInTestSourceContent(moduleFile);
      });
    }

    @Override
    public boolean isSearchInModuleContent(Module aModule) {
      return contains(findDescriptorByModule(aModule, myIsInTests));
    }

    @Override
    public boolean isSearchInLibraries() {
      return myIncludeLibraries;
    }

    @Override
    public boolean contains(VirtualFile file) {
      Project project = getProject();
      if (project == null) {
        return false;
      }
      if (!isJvmLanguageFile(file)) {
        return false;
      }
      ProjectFileIndex index = ProjectFileIndex.getInstance(project);
      if (index.isInLibrary(file)) {
        return myIncludeLibraries && contains(findDescriptorInLibrary(file, project));
      }
      Module module = index.getModuleForFile(file);
      return contains(findDescriptorByModule(module, myIsInTests));
    }

    private boolean contains(@Nullable PsiJavaModule module) {
      if (module == null || !module.isValid()) {
        return false;
      }
      Collection<PsiJavaModule> myCollectedModules = myModules.get(module.getName());
      return myCollectedModules.contains(module);
    }

    private static boolean isJvmLanguageFile(VirtualFile file) {
      FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();
      LanguageFileType languageFileType = tryCast(fileTypeRegistry.getFileTypeByFileName(file.getName()), LanguageFileType.class);
      return languageFileType != null && languageFileType.getLanguage() instanceof JavaLanguage;
    }

    public static
    @Nullable
    JavaModuleScope moduleScope(PsiJavaModule module) {
      PsiFile moduleFile = module.getContainingFile();
      if (moduleFile == null) {
        return null;
      }
      VirtualFile virtualFile = moduleFile.getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      return new JavaModuleScope(module.getProject(), Set.of(module));
    }

    /**
     * Creates a JavaModuleScope that includes the given module and all transitive modules.
     *
     * @param module the base PsiJavaModule for which to create the scope, must not be null
     * @return a new JavaModuleScope including all transitive modules of the given module, or null if the moduleFile is null or no transitive modules are found
     */
    public static
    @Nullable
    JavaModuleScope moduleWithTransitiveScope(PsiJavaModule module) {
      Set<PsiJavaModule> allModules = getAllTransitiveModulesIncludeCurrent(module);
      if (allModules.isEmpty()) {
        return null;
      }
      return new JavaModuleScope(module.getProject(), allModules);
    }
  }
}
