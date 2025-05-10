/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.JavaPsiImplementationHelper;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.java.language.impl.psi.impl.source.resolve.ClassResolverProcessor;
import com.intellij.java.language.impl.psi.impl.source.resolve.SymbolCollectingProcessor;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.function.Processor;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.impl.psi.PsiFileImpl;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.IndexingDataKeys;
import consulo.language.psi.stub.StubElement;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.collection.MostlySingularMultiMap;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.NotNullLazyKey;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

public abstract class PsiJavaFileBaseImpl extends PsiFileImpl implements PsiJavaFile {
  private static final Logger LOG = Logger.getInstance(PsiJavaFileBaseImpl.class);
  @NonNls
  private static final String[] IMPLICIT_IMPORTS = {CommonClassNames.DEFAULT_PACKAGE};
  private final CachedValue<MostlySingularMultiMap<String, SymbolCollectingProcessor.ResultWithContext>> myResolveCache;
  private volatile String myPackageName;

  protected PsiJavaFileBaseImpl(IElementType elementType, IElementType contentElementType, FileViewProvider viewProvider) {
    super(elementType, contentElementType, viewProvider);
    myResolveCache = CachedValuesManager.getManager(getManager().getProject()).createCachedValue(new MyCacheBuilder(this), false);
  }

  @Override
  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected PsiJavaFileBaseImpl clone() {
    PsiFileImpl clone = super.clone();
    if (!(clone instanceof PsiJavaFileBaseImpl)) {
      throw new AssertionError("Java file cloned as text: " + getTextLength() + "; " + getViewProvider());
    }
    clone.clearCaches();
    return (PsiJavaFileBaseImpl) clone;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myPackageName = null;
  }

  @Override
  @Nonnull
  public PsiClass[] getClasses() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(JavaStubElementTypes.CLASS, PsiClass.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(Constants.CLASS_BIT_SET, PsiClass.ARRAY_FACTORY);
  }

  @Override
  public PsiPackageStatement getPackageStatement() {
    ASTNode node = calcTreeElement().findChildByType(JavaElementType.PACKAGE_STATEMENT);
    return node != null ? (PsiPackageStatement) node.getPsi() : null;
  }

  @Override
  @Nonnull
  public String getPackageName() {
    PsiJavaFileStub stub = (PsiJavaFileStub) getStub();
    if (stub != null) {
      return stub.getPackageName();
    }

    String name = myPackageName;
    if (name == null) {
      PsiPackageStatement statement = getPackageStatement();
      myPackageName = name = statement == null ? "" : statement.getPackageName();
    }
    return name;
  }

  @Override
  public void setPackageName(final String packageName) throws IncorrectOperationException {
    final PsiPackageStatement packageStatement = getPackageStatement();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    if (packageStatement != null) {
      if (packageName.length() > 0) {
        final PsiJavaCodeReferenceElement reference = packageStatement.getPackageReference();
        reference.replace(factory.createReferenceFromText(packageName, packageStatement));
      } else {
        packageStatement.delete();
      }
    } else {
      if (packageName.length() > 0) {
        addBefore(factory.createPackageStatement(packageName), getFirstChild());
      }
    }
  }

  @Override
  @Nullable
  public PsiImportList getImportList() {
    StubElement<?> stub = getGreenStub();
    if (stub != null) {
      PsiImportList[] nodes = stub.getChildrenByType(JavaStubElementTypes.IMPORT_LIST, PsiImportList.ARRAY_FACTORY);
      if (nodes.length == 1) {
        return nodes[0];
      }
      assert nodes.length == 0;
      return null;
    }

    ASTNode node = calcTreeElement().findChildByType(JavaElementType.IMPORT_LIST);
    return (PsiImportList) SourceTreeToPsiMap.treeElementToPsi(node);
  }

  @Override
  @Nonnull
  public PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
    List<PsiElement> array = new ArrayList<PsiElement>();

    PsiImportList importList = getImportList();
    PsiImportStatement[] statements = importList.getImportStatements();
    for (PsiImportStatement statement : statements) {
      if (statement.isOnDemand()) {
        PsiElement resolved = statement.resolve();
        if (resolved != null) {
          array.add(resolved);
        }
      }
    }

    if (includeImplicit) {
      PsiJavaCodeReferenceElement[] implicitRefs = getImplicitlyImportedPackageReferences();
      for (PsiJavaCodeReferenceElement implicitRef : implicitRefs) {
        final PsiElement resolved = implicitRef.resolve();
        if (resolved != null) {
          array.add(resolved);
        }
      }
    }

    return PsiUtilCore.toPsiElementArray(array);
  }

  @Override
  @Nonnull
  public PsiClass[] getSingleClassImports(boolean checkIncludes) {
    List<PsiClass> array = new ArrayList<PsiClass>();
    PsiImportList importList = getImportList();
    PsiImportStatement[] statements = importList.getImportStatements();
    for (PsiImportStatement statement : statements) {
      if (!statement.isOnDemand()) {
        PsiElement ref = statement.resolve();
        if (ref instanceof PsiClass) {
          array.add((PsiClass) ref);
        }
      }
    }
    return array.toArray(new PsiClass[array.size()]);
  }

  @Override
  public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
    PsiImportList importList = getImportList();
    PsiImportStatement[] statements = importList.getImportStatements();
    for (PsiImportStatement statement : statements) {
      if (!statement.isOnDemand()) {
        PsiElement ref = statement.resolve();
        if (ref != null && getManager().areElementsEquivalent(ref, aClass)) {
          return statement.getImportReference();
        }
      }
    }
    return null;
  }

  @Override
  @Nonnull
  public String[] getImplicitlyImportedPackages() {
    return IMPLICIT_IMPORTS;
  }

  @Override
  @Nonnull
  public PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences() {
    return PsiImplUtil.namesToPackageReferences(getManager(), IMPLICIT_IMPORTS);
  }

  private static class StaticImportFilteringProcessor implements PsiScopeProcessor {
    private final PsiScopeProcessor myDelegate;
    private boolean myIsProcessingOnDemand;
    private final Collection<String> myHiddenNames = new HashSet<String>();
    private final Collection<PsiElement> myCollectedElements = new HashSet<PsiElement>();

    public StaticImportFilteringProcessor(final PsiScopeProcessor delegate) {
      myDelegate = delegate;
    }

    @Override
    public <T> T getHint(@Nonnull final Key<T> hintKey) {
      return myDelegate.getHint(hintKey);
    }

    @Override
    public void handleEvent(final Event event, final Object associated) {
      if (JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT.equals(event) && associated instanceof PsiImportStaticStatement) {
        final PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement) associated;
        myIsProcessingOnDemand = importStaticStatement.isOnDemand();
        if (!myIsProcessingOnDemand) {
          myHiddenNames.add(importStaticStatement.getReferenceName());
        }
      }
      myDelegate.handleEvent(event, associated);
    }

    @Override
    public boolean execute(@Nonnull final PsiElement element, final ResolveState state) {
      if (element instanceof PsiModifierListOwner && ((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC)) {
        if (element instanceof PsiNamedElement && myIsProcessingOnDemand) {
          final String name = ((PsiNamedElement) element).getName();
          if (myHiddenNames.contains(name)) {
            return true;
          }
        }
        if (myCollectedElements.add(element)) {
          return myDelegate.execute(element, state);
        }
      }
      return true;
    }
  }

  @Override
  public boolean processDeclarations(@Nonnull final PsiScopeProcessor processor, @Nonnull final ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    assert isValid();

    if (processor instanceof ClassResolverProcessor &&
        isPhysical() &&
        (getUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING) == Boolean.TRUE || myResolveCache.hasUpToDateValue())) {
      final ClassResolverProcessor hint = (ClassResolverProcessor) processor;
      String name = hint.getName(state);
      MostlySingularMultiMap<String, SymbolCollectingProcessor.ResultWithContext> cache = myResolveCache.getValue();
      MyResolveCacheProcessor cacheProcessor = new MyResolveCacheProcessor(processor, state);
      return name != null ? cache.processForKey(name, cacheProcessor) : cache.processAllValues(cacheProcessor);
    }

    return processDeclarationsNoGuess(processor, state, lastParent, place);
  }

  private boolean processDeclarationsNoGuess(PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    final NameHint nameHint = processor.getHint(NameHint.KEY);
    final String name = nameHint != null ? nameHint.getName(state) : null;
    final PsiImportList importList = getImportList();

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      final PsiClass[] classes = getClasses();
      for (PsiClass aClass : classes) {
        if (!processor.execute(aClass, state)) {
          return false;
        }
      }

      final PsiImportStatement[] importStatements = importList.getImportStatements();

      // single-type processing
      for (PsiImportStatement statement : importStatements) {
        if (!statement.isOnDemand()) {
          if (name != null) {
            final String refText = statement.getQualifiedName();
            if (refText == null || !refText.endsWith(name)) {
              continue;
            }
          }

          final PsiElement resolved = statement.resolve();
          if (resolved instanceof PsiClass) {
            processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, statement);
            final PsiClass containingClass = ((PsiClass) resolved).getContainingClass();
            if (containingClass != null && containingClass.hasTypeParameters()) {
              if (!processor.execute(resolved, state.put(PsiSubstitutor.KEY, createRawSubstitutor(containingClass)))) {
                return false;
              }
            } else if (!processor.execute(resolved, state)) {
              return false;
            }
          }
        }
      }
      processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, null);

      // check in current package
      final PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(getPackageName());
      if (aPackage != null) {
        if (!aPackage.processDeclarations(processor, state, null, place)) {
          return false;
        }
      }

      // on-demand processing
      for (PsiImportStatement statement : importStatements) {
        if (statement.isOnDemand()) {
          final PsiElement resolved = statement.resolve();
          if (resolved != null) {
            processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, statement);
            processOnDemandTarget(resolved, processor, state, place);
          }
        }
      }
    }

    final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    if (importStaticStatements.length > 0) {
      final StaticImportFilteringProcessor staticImportProcessor = new StaticImportFilteringProcessor(processor);

      // single member processing
      for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
        if (importStaticStatement.isOnDemand()) {
          continue;
        }
        final PsiJavaCodeReferenceElement reference = importStaticStatement.getImportReference();
        if (reference != null) {
          final JavaResolveResult[] results = reference.multiResolve(false);
          if (results.length > 0) {
            staticImportProcessor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, importStaticStatement);
            for (JavaResolveResult result : results) {
              if (!staticImportProcessor.execute(result.getElement(), state)) {
                return false;
              }
            }
          }
        }
      }

      // on-demand processing
      for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
        if (!importStaticStatement.isOnDemand()) {
          continue;
        }
        final PsiClass targetElement = importStaticStatement.resolveTargetClass();
        if (targetElement != null) {
          staticImportProcessor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, importStaticStatement);
          if (!targetElement.processDeclarations(staticImportProcessor, state, lastParent, place)) {
            return false;
          }
        }
      }
    }

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      processor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, null);

      final PsiJavaCodeReferenceElement[] implicitlyImported = getImplicitlyImportedPackageReferences();
      for (PsiJavaCodeReferenceElement aImplicitlyImported : implicitlyImported) {
        final PsiElement resolved = aImplicitlyImported.resolve();
        if (resolved != null) {
          if (!processOnDemandTarget(resolved, processor, state, place)) {
            return false;
          }
        }
      }
    }

    return true;
  }

  @Nonnull
  private static PsiSubstitutor createRawSubstitutor(PsiClass containingClass) {
    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createRawSubstitutor(containingClass);
  }

  private static boolean processOnDemandTarget(PsiElement target, PsiScopeProcessor processor, ResolveState substitutor, PsiElement place) {
    if (target instanceof PsiPackage) {
      if (!target.processDeclarations(processor, substitutor, null, place)) {
        return false;
      }
    } else if (target instanceof PsiClass) {
      PsiClass[] inners = ((PsiClass) target).getInnerClasses();
      if (((PsiClass) target).hasTypeParameters()) {
        substitutor = substitutor.put(PsiSubstitutor.KEY, createRawSubstitutor((PsiClass) target));
      }

      for (PsiClass inner : inners) {
        if (!processor.execute(inner, substitutor)) {
          return false;
        }
      }
    } else {
      LOG.assertTrue(false);
    }
    return true;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitJavaFile(this);
    } else {
      visitor.visitFile(this);
    }
  }

  @Override
  @Nonnull
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public boolean importClass(PsiClass aClass) {
    return JavaCodeStyleManager.getInstance(getProject()).addImport(this, aClass);
  }

  private static final NotNullLazyKey<LanguageLevel, PsiJavaFileBaseImpl> LANGUAGE_LEVEL_KEY = NotNullLazyKey.create("LANGUAGE_LEVEL", new Function<PsiJavaFileBaseImpl, LanguageLevel>() {
    @Override
    @Nonnull
    public LanguageLevel apply(PsiJavaFileBaseImpl file) {
      return file.getLanguageLevelInner();
    }
  });

  @RequiredReadAction
  @Nullable
  @Override
  public PsiJavaModule getModuleDeclaration() {
    return null;
  }

  @Override
  @Nonnull
  public LanguageLevel getLanguageLevel() {
    return LANGUAGE_LEVEL_KEY.getValue(this);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    putUserData(LANGUAGE_LEVEL_KEY, null);
  }

  private LanguageLevel getLanguageLevelInner() {
    if (myOriginalFile instanceof PsiJavaFile) {
      return ((PsiJavaFile) myOriginalFile).getLanguageLevel();
    }
    final LanguageLevel forcedLanguageLevel = getUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY);
    if (forcedLanguageLevel != null) {
      return forcedLanguageLevel;
    }
    VirtualFile virtualFile = getVirtualFile();

    if (virtualFile == null) {
      virtualFile = getUserData(IndexingDataKeys.VIRTUAL_FILE);
    }

    final Project project = getProject();
    if (virtualFile == null) {
      final PsiFile originalFile = getOriginalFile();
      if (originalFile instanceof PsiJavaFile && originalFile != this) {
        return ((PsiJavaFile) originalFile).getLanguageLevel();
      }

      final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(originalFile);
      if (moduleForPsiElement != null) {
        return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(moduleForPsiElement);
      }
    } else {
      final VirtualFile folder = virtualFile.getParent();
      if (folder != null) {
        final LanguageLevel level = folder.getUserData(LanguageLevel.KEY);
        if (level != null) {
          return level;
        }
      }

      final LanguageLevel classesLanguageLevel = JavaPsiImplementationHelper.getInstance(project).getClassesLanguageLevel(virtualFile);
      if (classesLanguageLevel != null) {
        return classesLanguageLevel;
      }
    }
    return LanguageLevel.HIGHEST;
  }

  private static class MyCacheBuilder implements CachedValueProvider<MostlySingularMultiMap<String, SymbolCollectingProcessor.ResultWithContext>> {
    private final PsiJavaFileBaseImpl myFile;

    public MyCacheBuilder(PsiJavaFileBaseImpl file) {
      myFile = file;
    }

    @Override
    public Result<MostlySingularMultiMap<String, SymbolCollectingProcessor.ResultWithContext>> compute() {
      SymbolCollectingProcessor p = new SymbolCollectingProcessor();
      myFile.processDeclarationsNoGuess(p, ResolveState.initial(), myFile, myFile);
      MostlySingularMultiMap<String, SymbolCollectingProcessor.ResultWithContext> results = p.getResults();
      return Result.create(results, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    }
  }

  private static class MyResolveCacheProcessor implements Processor<SymbolCollectingProcessor.ResultWithContext> {
    private final PsiScopeProcessor myProcessor;
    private final ResolveState myState;

    public MyResolveCacheProcessor(PsiScopeProcessor processor, ResolveState state) {
      myProcessor = processor;
      myState = state;
    }

    @Override
    public boolean process(SymbolCollectingProcessor.ResultWithContext result) {
      final PsiElement context = result.getFileContext();
      myProcessor.handleEvent(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT, context);
      final PsiNamedElement element = result.getElement();

      if (element instanceof PsiClass && context instanceof PsiImportStatement) {
        final PsiClass containingClass = ((PsiClass) element).getContainingClass();
        if (containingClass != null && containingClass.hasTypeParameters()) {
          return myProcessor.execute(element, myState.put(PsiSubstitutor.KEY, createRawSubstitutor(containingClass)));
        }
      }

      return myProcessor.execute(element, myState);
    }
  }
}
