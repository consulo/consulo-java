/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 12, 2001
 * Time: 9:40:45 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */

package com.intellij.java.analysis.impl.codeInspection.deadCode;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.ex.EntryPointProvider;
import com.intellij.java.analysis.codeInspection.ex.EntryPointState;
import com.intellij.java.analysis.codeInspection.ex.EntryPointsManager;
import com.intellij.java.analysis.codeInspection.reference.*;
import com.intellij.java.analysis.impl.codeInspection.reference.RefClassImpl;
import com.intellij.java.analysis.impl.codeInspection.reference.RefJavaElementImpl;
import com.intellij.java.analysis.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.java.analysis.impl.codeInspection.util.RefFilter;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.codeInsight.daemon.impl.analysis.HighlightUtilBase;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.Language;
import consulo.language.editor.ImplicitUsageProvider;
import consulo.language.editor.impl.inspection.reference.RefElementImpl;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.GlobalInspectionTool;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.ProblemDescriptionsProcessor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.reference.RefUtil;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.inspection.scheme.JobDescriptor;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.DelegatingGlobalSearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiNonJavaFileReferenceProcessor;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public abstract class UnusedDeclarationInspectionBase extends GlobalInspectionTool implements OldStyleInspection {
  @Deprecated
  public boolean ADD_MAINS_TO_ENTRIES = true;
  @Deprecated
  public boolean ADD_APPLET_TO_ENTRIES = true;
  @Deprecated
  public boolean ADD_SERVLET_TO_ENTRIES = true;
  @Deprecated
  public boolean ADD_NONJAVA_TO_ENTRIES = true;

  private Set<RefElement> myProcessedSuspicious = null;
  private int myPhase;
  @NonNls
  public static final String SHORT_NAME = "unused";
  @NonNls
  public static final String ALTERNATIVE_ID = "UnusedDeclaration";

  private static final Logger LOG = Logger.getInstance(UnusedDeclarationInspectionBase.class);
  private GlobalInspectionContext myContext;
  protected final UnusedSymbolLocalInspectionBase myLocalInspectionBase = createUnusedSymbolLocalInspection();

  @Nonnull
  @Override
  public InspectionToolState<?> createStateProvider() {
    return new UnusedDeclarationInspectionState();
  }

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  protected UnusedSymbolLocalInspectionBase createUnusedSymbolLocalInspection() {
    return new UnusedSymbolLocalInspectionBase();
  }

  @Nullable
  @Override
  public UnusedSymbolLocalInspectionBase getSharedLocalInspectionTool() {
    return myLocalInspectionBase;
  }

  protected GlobalInspectionContext getContext() {
    return myContext;
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionLocalize.inspectionDeadCodeDisplayName().get();
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesDeclarationRedundancy().get();
  }

  @Override
  @Nonnull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  private static boolean isExternalizableNoParameterConstructor(@Nonnull PsiMethod method, RefClass refClass) {
    if (!method.isConstructor()) {
      return false;
    }
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() != 0) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    return aClass == null || isExternalizable(aClass, refClass);
  }

  private static boolean isSerializationImplicitlyUsedField(@Nonnull PsiField field) {
    @NonNls final String name = field.getName();
    if (!HighlightUtilBase.SERIAL_VERSION_UID_FIELD_NAME.equals(name) && !"serialPersistentFields".equals(name)) {
      return false;
    }
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    PsiClass aClass = field.getContainingClass();
    return aClass == null || isSerializable(aClass, null);
  }

  private static boolean isWriteObjectMethod(@Nonnull PsiMethod method, RefClass refClass) {
    @NonNls final String name = method.getName();
    if (!"writeObject".equals(name)) {
      return false;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) {
      return false;
    }
    if (!parameters[0].getType().equalsToText("java.io.ObjectOutputStream")) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isReadObjectMethod(@Nonnull PsiMethod method, RefClass refClass) {
    @NonNls final String name = method.getName();
    if (!"readObject".equals(name)) {
      return false;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) {
      return false;
    }
    if (!parameters[0].getType().equalsToText("java.io.ObjectInputStream")) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isWriteReplaceMethod(@Nonnull PsiMethod method, RefClass refClass) {
    @NonNls final String name = method.getName();
    if (!"writeReplace".equals(name)) {
      return false;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) {
      return false;
    }
    if (!method.getReturnType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isReadResolveMethod(@Nonnull PsiMethod method, RefClass refClass) {
    @NonNls final String name = method.getName();
    if (!"readResolve".equals(name)) {
      return false;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) {
      return false;
    }
    if (!method.getReturnType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    return !(aClass != null && !isSerializable(aClass, refClass));
  }

  private static boolean isSerializable(PsiClass aClass, @Nullable RefClass refClass) {
    final PsiClass serializableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.io" +
                                                                                                  ".Serializable",
                                                                                                aClass.getResolveScope());
    return serializableClass != null && isSerializable(aClass, refClass, serializableClass);
  }

  private static boolean isExternalizable(@Nonnull PsiClass aClass, RefClass refClass) {
    final GlobalSearchScope scope = aClass.getResolveScope();
    final PsiClass externalizableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.io" +
                                                                                                    ".Externalizable", scope);
    return externalizableClass != null && isSerializable(aClass, refClass, externalizableClass);
  }

  private static boolean isSerializable(PsiClass aClass, RefClass refClass, PsiClass serializableClass) {
    if (aClass == null) {
      return false;
    }
    if (aClass.isInheritor(serializableClass, true)) {
      return true;
    }
    if (refClass != null) {
      final Set<RefClass> subClasses = refClass.getSubClasses();
      for (RefClass subClass : subClasses) {
        if (isSerializable(subClass.getElement(), subClass, serializableClass)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void runInspection(
    @Nonnull final AnalysisScope scope,
    @Nonnull InspectionManager manager,
    @Nonnull final GlobalInspectionContext globalContext,
    @Nonnull ProblemDescriptionsProcessor problemDescriptionsProcessor,
    @Nonnull Object state
  ) {
    UnusedDeclarationInspectionState inspectionState = (UnusedDeclarationInspectionState)state;

    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@Nonnull final RefEntity refEntity) {
        if (refEntity instanceof RefJavaElement) {
          final RefElementImpl refElement = (RefElementImpl)refEntity;
          if (!refElement.isSuspicious()) {
            return;
          }

          PsiFile file = refElement.getContainingFile();

          if (file == null) {
            return;
          }
          final boolean isSuppressed = refElement.isSuppressed(getShortName(), ALTERNATIVE_ID);
          if (isSuppressed || !globalContext.isToCheckFile(file, UnusedDeclarationInspectionBase.this)) {
            if (isSuppressed || !scope.contains(file)) {
              getEntryPointsManager().addEntryPoint(refElement, false);
            }
            return;
          }

          refElement.accept(new RefJavaVisitor() {
            @Override
            public void visitMethod(@Nonnull RefMethod method) {
              if (inspectionState.isAddMainsEnabled() && method.isAppMain()) {
                getEntryPointsManager().addEntryPoint(method, false);
              }
            }

            @Override
            public void visitClass(@Nonnull RefClass aClass) {
              if (inspectionState.isAddAppletEnabled() && aClass.isApplet() || inspectionState.isAddServletEnabled() && aClass.isServlet()) {
                getEntryPointsManager().addEntryPoint(aClass, false);
              }
            }
          });
        }
      }
    });

    if (inspectionState.isAddNonJavaUsedEnabled()) {
      checkForReachables(globalContext);
      final StrictUnreferencedFilter strictUnreferencedFilter = new StrictUnreferencedFilter(this,
                                                                                             globalContext);
      ProgressManager.getInstance().runProcess(new Runnable() {
        @Override
        public void run() {
          final RefManager refManager = globalContext.getRefManager();
          final PsiSearchHelper helper = PsiSearchHelper.getInstance(refManager.getProject());
          refManager.iterate(new RefJavaVisitor() {
            @Override
            public void visitElement(@Nonnull final RefEntity refEntity) {
              if (refEntity instanceof RefClass && strictUnreferencedFilter.accepts((RefClass)refEntity)) {
                findExternalClassReferences((RefClass)refEntity);
              }
              else if (refEntity instanceof RefMethod) {
                RefMethod refMethod = (RefMethod)refEntity;
                if (refMethod.isConstructor() && strictUnreferencedFilter.accepts(refMethod)) {
                  findExternalClassReferences(refMethod.getOwnerClass());
                }
              }
            }

            private void findExternalClassReferences(final RefClass refElement) {
              final PsiClass psiClass = refElement.getElement();
              String qualifiedName = psiClass != null ? psiClass.getQualifiedName() : null;
              if (qualifiedName != null) {
                final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(globalContext
                                                                                        .getProject());
                final PsiNonJavaFileReferenceProcessor processor = (file, startOffset, endOffset) -> {
                  getEntryPointsManager().addEntryPoint(refElement, false);
                  return false;
                };
                final DelegatingGlobalSearchScope globalSearchScope = new DelegatingGlobalSearchScope
                  (projectScope) {
                  @Override
                  public boolean contains(@Nonnull VirtualFile file) {
                    return file.getFileType() != JavaFileType.INSTANCE && super.contains(file);
                  }
                };

                if (helper.processUsagesInNonJavaFiles(qualifiedName, processor, globalSearchScope)) {
                  final PsiReference reference = ReferencesSearch.search(psiClass,
                                                                         globalSearchScope).findFirst();
                  if (reference != null) {
                    getEntryPointsManager().addEntryPoint(refElement, false);
                    for (PsiMethod method : psiClass.getMethods()) {
                      final RefElement refMethod = refManager.getReference(method);
                      if (refMethod != null) {
                        getEntryPointsManager().addEntryPoint(refMethod, false);
                      }
                    }
                  }
                }
              }
            }
          });
        }
      }, null);
    }

    myProcessedSuspicious = new HashSet<>();
    myPhase = 1;
  }

  @SuppressWarnings("unchecked")
  public boolean isEntryPoint(@Nonnull RefElement owner, @Nonnull UnusedDeclarationInspectionState state) {
    final PsiElement element = owner.getPsiElement();
    if (RefUtil.isImplicitUsage(element)) {
      return true;
    }
    if (element instanceof PsiModifierListOwner) {
      final EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(element.getProject());
      if (entryPointsManager.isEntryPoint(element)) {
        return true;
      }
    }
    if (element != null) {
      for (EntryPointProvider<EntryPointState> extension : Application.get().getExtensionList(EntryPointProvider.class)) {
        EntryPointState pointState = state.getEntryPointState(extension);

        if (extension.isEntryPoint(owner, element, pointState)) {
          return true;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  public boolean isEntryPoint(@Nonnull PsiElement element, @Nonnull UnusedDeclarationInspectionState state) {
    final Project project = element.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    if (element instanceof PsiMethod && state.isAddMainsEnabled() && PsiClassImplUtil.isMainOrPremainMethod((PsiMethod)
                                                                                                              element)) {
      return true;
    }
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      final PsiClass applet = psiFacade.findClass("java.applet.Applet", GlobalSearchScope.allScope(project));
      if (state.isAddAppletEnabled() && applet != null && aClass.isInheritor(applet, true)) {
        return true;
      }

      final PsiClass servlet = psiFacade.findClass("javax.servlet.Servlet", GlobalSearchScope.allScope(project));
      if (state.isAddServletEnabled() && servlet != null && aClass.isInheritor(servlet, true)) {
        return true;
      }
      if (state.isAddMainsEnabled() && PsiMethodUtil.hasMainMethod(aClass)) {
        return true;
      }
    }
    if (element instanceof PsiModifierListOwner) {
      final EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(project);
      if (entryPointsManager.isEntryPoint(element)) {
        return true;
      }
    }

    for (EntryPointProvider<EntryPointState> extension : Application.get().getExtensionList(EntryPointProvider.class)) {
      EntryPointState pointState = state.getEntryPointState(extension);

      if (extension.isEntryPoint(element, pointState)) {
        return true;
      }
    }

    return project.getExtensionPoint(ImplicitUsageProvider.class).findFirstSafe(it -> it.isImplicitUsage(element)) != null;
  }

  public boolean isGlobalEnabledInEditor() {
    return true;
  }

  private static class StrictUnreferencedFilter extends UnreferencedFilter {
    private StrictUnreferencedFilter(@Nonnull UnusedDeclarationInspectionBase tool,
                                     @Nonnull GlobalInspectionContext context) {
      super(tool, context);
    }

    @Override
    public int getElementProblemCount(@Nonnull RefJavaElement refElement) {
      final int problemCount = super.getElementProblemCount(refElement);
      if (problemCount > -1) {
        return problemCount;
      }
      return refElement.isReferenced() ? 0 : 1;
    }
  }

  @Override
  public boolean queryExternalUsagesRequests(@Nonnull InspectionManager manager,
                                             @Nonnull GlobalInspectionContext globalContext,
                                             @Nonnull ProblemDescriptionsProcessor problemDescriptionsProcessor,
                                             @Nonnull Object state) {
    checkForReachables(globalContext);
    final RefFilter filter = myPhase == 1 ? new StrictUnreferencedFilter(this,
                                                                         globalContext) : new RefUnreachableFilter(this, globalContext);
    final boolean[] requestAdded = {false};

    globalContext.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@Nonnull RefEntity refEntity) {
        if (!(refEntity instanceof RefJavaElement)) {
          return;
        }
        if (refEntity instanceof RefClass && ((RefClass)refEntity).isAnonymous()) {
          return;
        }
        RefJavaElement refElement = (RefJavaElement)refEntity;
        if (filter.accepts(refElement) && !myProcessedSuspicious.contains(refElement)) {
          refEntity.accept(new RefJavaVisitor() {
            @Override
            public void visitField(@Nonnull final RefField refField) {
              myProcessedSuspicious.add(refField);
              PsiField psiField = refField.getElement();
              if (psiField != null && isSerializationImplicitlyUsedField(psiField)) {
                getEntryPointsManager().addEntryPoint(refField, false);
              }
              else {
                getJavaContext().enqueueFieldUsagesProcessor(refField, psiReference -> {
                  getEntryPointsManager().addEntryPoint(refField, false);
                  return false;
                });
                requestAdded[0] = true;
              }
            }

            @Override
            public void visitMethod(@Nonnull final RefMethod refMethod) {
              myProcessedSuspicious.add(refMethod);
              if (refMethod instanceof RefImplicitConstructor) {
                visitClass(refMethod.getOwnerClass());
              }
              else {
                PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
                if (psiMethod != null && isSerializablePatternMethod(psiMethod,
                                                                     refMethod.getOwnerClass())) {
                  getEntryPointsManager().addEntryPoint(refMethod, false);
                }
                else if (!refMethod.isExternalOverride() && !PsiModifier.PRIVATE.equals(refMethod
                                                                                          .getAccessModifier())) {
                  for (final RefMethod derivedMethod : refMethod.getDerivedMethods()) {
                    myProcessedSuspicious.add(derivedMethod);
                  }

                  enqueueMethodUsages(refMethod);
                  requestAdded[0] = true;
                }
              }
            }

            @Override
            public void visitClass(@Nonnull final RefClass refClass) {
              myProcessedSuspicious.add(refClass);
              if (!refClass.isAnonymous()) {
                getJavaContext().enqueueDerivedClassesProcessor(
                  refClass,
                  inheritor -> {
                    getEntryPointsManager().addEntryPoint(refClass, false);
                    return false;
                  }
                );

                getJavaContext().enqueueClassUsagesProcessor(
                  refClass,
                  psiReference -> {
                    getEntryPointsManager().addEntryPoint(refClass, false);
                    return false;
                  }
                );
                requestAdded[0] = true;
              }
            }
          });
        }
      }
    });

    if (!requestAdded[0]) {
      if (myPhase == 2) {
        myProcessedSuspicious = null;
        return false;
      }
      else {
        myPhase = 2;
      }
    }

    return true;
  }

  private static boolean isSerializablePatternMethod(@Nonnull PsiMethod psiMethod, RefClass refClass) {
    return isReadObjectMethod(psiMethod, refClass) || isWriteObjectMethod(psiMethod,
                                                                          refClass) || isReadResolveMethod(psiMethod, refClass) ||
      isWriteReplaceMethod(psiMethod, refClass) || isExternalizableNoParameterConstructor(psiMethod,
                                                                                          refClass);
  }

  private void enqueueMethodUsages(final RefMethod refMethod) {
    if (refMethod.getSuperMethods().isEmpty()) {
      getJavaContext().enqueueMethodUsagesProcessor(refMethod, psiReference -> {
        getEntryPointsManager().addEntryPoint(refMethod, false);
        return false;
      });
    }
    else {
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        enqueueMethodUsages(refSuper);
      }
    }
  }

  private GlobalJavaInspectionContext getJavaContext() {
    return getContext().getExtension(GlobalJavaInspectionContext.CONTEXT);
  }

  @Nullable
  @Override
  public JobDescriptor[] getAdditionalJobs() {
    return new JobDescriptor[]{
      getContext().getStdJobDescriptors().BUILD_GRAPH,
      getContext().getStdJobDescriptors().FIND_EXTERNAL_USAGES
    };
  }

  public void checkForReachables(@Nonnull final GlobalInspectionContext context) {
    CodeScanner codeScanner = new CodeScanner();

    // Cleanup previous reachability information.
    context.getRefManager().iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@Nonnull RefEntity refEntity) {
        if (refEntity instanceof RefJavaElement) {
          final RefJavaElementImpl refElement = (RefJavaElementImpl)refEntity;
          if (!context.isToCheckMember(refElement, UnusedDeclarationInspectionBase.this)) {
            return;
          }
          refElement.setReachable(false);
        }
      }
    });


    for (RefElement entry : getEntryPointsManager().getEntryPoints()) {
      entry.accept(codeScanner);
    }

    while (codeScanner.newlyInstantiatedClassesCount() != 0) {
      codeScanner.cleanInstantiatedClassesCount();
      codeScanner.processDelayedMethods();
    }
  }

  private EntryPointsManager getEntryPointsManager() {
    return getJavaContext().getEntryPointsManager(getContext().getRefManager());
  }

  private static class CodeScanner extends RefJavaVisitor {
    private final Map<RefClass, Set<RefMethod>> myClassIDtoMethods = new HashMap<>();
    private final Set<RefClass> myInstantiatedClasses = new HashSet<>();
    private int myInstantiatedClassesCount;
    private final Set<RefMethod> myProcessedMethods = new HashSet<>();

    @Override
    public void visitMethod(@Nonnull RefMethod method) {
      if (!myProcessedMethods.contains(method)) {
        // Process class's static intitializers
        if (method.isStatic() || method.isConstructor()) {
          if (method.isConstructor()) {
            addInstantiatedClass(method.getOwnerClass());
          }
          else {
            ((RefClassImpl)method.getOwnerClass()).setReachable(true);
          }
          myProcessedMethods.add(method);
          makeContentReachable((RefJavaElementImpl)method);
          makeClassInitializersReachable(method.getOwnerClass());
        }
        else {
          if (isClassInstantiated(method.getOwnerClass())) {
            myProcessedMethods.add(method);
            makeContentReachable((RefJavaElementImpl)method);
          }
          else {
            addDelayedMethod(method);
          }

          for (RefMethod refSub : method.getDerivedMethods()) {
            visitMethod(refSub);
          }
        }
      }
    }

    @Override
    public void visitClass(@Nonnull RefClass refClass) {
      boolean alreadyActive = refClass.isReachable();
      ((RefClassImpl)refClass).setReachable(true);

      if (!alreadyActive) {
        // Process class's static intitializers.
        makeClassInitializersReachable(refClass);
      }

      addInstantiatedClass(refClass);
    }

    @Override
    public void visitField(@Nonnull RefField field) {
      // Process class's static intitializers.
      if (!field.isReachable()) {
        makeContentReachable((RefJavaElementImpl)field);
        makeClassInitializersReachable(field.getOwnerClass());
      }
    }

    private void addInstantiatedClass(RefClass refClass) {
      if (myInstantiatedClasses.add(refClass)) {
        ((RefClassImpl)refClass).setReachable(true);
        myInstantiatedClassesCount++;

        final List<RefMethod> refMethods = refClass.getLibraryMethods();
        for (RefMethod refMethod : refMethods) {
          refMethod.accept(this);
        }
        for (RefClass baseClass : refClass.getBaseClasses()) {
          addInstantiatedClass(baseClass);
        }
      }
    }

    private void makeContentReachable(RefJavaElementImpl refElement) {
      refElement.setReachable(true);
      for (RefElement refCallee : refElement.getOutReferences()) {
        refCallee.accept(this);
      }
    }

    private void makeClassInitializersReachable(RefClass refClass) {
      for (RefElement refCallee : refClass.getOutReferences()) {
        refCallee.accept(this);
      }
    }

    private void addDelayedMethod(RefMethod refMethod) {
      Set<RefMethod> methods = myClassIDtoMethods.get(refMethod.getOwnerClass());
      if (methods == null) {
        methods = new HashSet<>();
        myClassIDtoMethods.put(refMethod.getOwnerClass(), methods);
      }
      methods.add(refMethod);
    }

    private boolean isClassInstantiated(RefClass refClass) {
      return myInstantiatedClasses.contains(refClass);
    }

    private int newlyInstantiatedClassesCount() {
      return myInstantiatedClassesCount;
    }

    private void cleanInstantiatedClassesCount() {
      myInstantiatedClassesCount = 0;
    }

    private void processDelayedMethods() {
      RefClass[] instClasses = myInstantiatedClasses.toArray(new RefClass[myInstantiatedClasses.size()]);
      for (RefClass refClass : instClasses) {
        if (isClassInstantiated(refClass)) {
          Set<RefMethod> methods = myClassIDtoMethods.get(refClass);
          if (methods != null) {
            RefMethod[] arMethods = methods.toArray(new RefMethod[methods.size()]);
            for (RefMethod arMethod : arMethods) {
              arMethod.accept(this);
            }
          }
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void initialize(@Nonnull GlobalInspectionContext context, @Nonnull Object state) {
    super.initialize(context, state);
    myContext = context;
    UnusedDeclarationInspectionState inspectionState = (UnusedDeclarationInspectionState)state;
    for (EntryPointProvider<EntryPointState> extension : Application.get().getExtensionList(EntryPointProvider.class)) {
      inspectionState.ENTRY_POINTS.put(extension.getId(), extension.createState());
    }
  }

  @Override
  public void cleanup(Project project) {
    super.cleanup(project);
    myContext = null;
  }

  @Override
  public boolean isGraphNeeded() {
    return true;
  }
}
