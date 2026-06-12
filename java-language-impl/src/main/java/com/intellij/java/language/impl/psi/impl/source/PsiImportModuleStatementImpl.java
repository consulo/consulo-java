// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.PsiJavaModuleModificationTracker;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiImportModuleStatement;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiJavaModuleReference;
import com.intellij.java.language.psi.PsiJavaModuleReferenceElement;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import com.intellij.java.language.psi.util.JavaModuleGraphHelper;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.project.content.ProjectRootModificationTracker;
import consulo.util.collection.ArrayFactory;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SoftReference;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PsiImportModuleStatementImpl extends PsiImportStatementBaseImpl implements PsiImportModuleStatement {
  public static final PsiImportModuleStatementImpl[] EMPTY_ARRAY = new PsiImportModuleStatementImpl[0];
  public static final ArrayFactory<PsiImportModuleStatementImpl> ARRAY_FACTORY =
    count -> count == 0 ? EMPTY_ARRAY : new PsiImportModuleStatementImpl[count];

  private SoftReference<PsiJavaModuleReferenceElement> myRefElement;

  public PsiImportModuleStatementImpl(PsiImportStatementStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_MODULE_STATEMENT);
  }

  public PsiImportModuleStatementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable PsiJavaModule resolveTargetModule() {
    PsiJavaModuleReferenceElement refElement = getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    if (ref == null) return null;
    return ref.resolve();
  }

  @Override
  public String getReferenceName() {
    PsiJavaModuleReferenceElement refElement = getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    if (ref == null) return null;
    return ref.getCanonicalText();
  }

  @Override
  public @Nullable PsiJavaModuleReferenceElement getModuleReference() {
    PsiImportStatementStub stub = getStub();
    if (stub != null) {
      String refText = StringUtil.nullize(stub.getImportReferenceText());
      if (refText == null) return null;
      PsiJavaModuleReferenceElement refElement = SoftReference.dereference(myRefElement);
      if (refElement == null) {
        refElement = JavaPsiFacade.getInstance(getProject()).getParserFacade().createModuleReferenceFromText(refText, this);
        myRefElement = new SoftReference<>(refElement);
      }
      return refElement;
    }
    else {
      myRefElement = null;
      return PsiTreeUtil.getChildOfType(this, PsiJavaModuleReferenceElement.class);
    }
  }

  @Override
  public @Nullable PsiPackageAccessibilityStatement findImportedPackage(String packageName) {
    PsiImportModuleStatementImpl moduleStatement = this;
    return LanguageCachedValueUtil.getCachedValue(moduleStatement, () -> {
      Project project = moduleStatement.getProject();
      Map<String, PsiPackageAccessibilityStatement> packagesByName = new HashMap<>();
      PsiJavaModule module = resolveTargetModule();
      if (module == null) {
        return CachedValueProvider.Result.create(packagesByName,
                                                 PsiJavaModuleModificationTracker.getInstance(project),
                                                 ProjectRootModificationTracker.getInstance(project));
      }
      List<PsiPackageAccessibilityStatement> packages = JavaModuleGraphHelper.getInstance().getExportedPackages(module, module);
      for (PsiPackageAccessibilityStatement aPackage : packages) {
        String currentPackageName = aPackage.getPackageName();
        if (currentPackageName == null) continue;
        packagesByName.put(currentPackageName, aPackage);
      }
      return CachedValueProvider.Result.create(packagesByName,
                                               PsiJavaModuleModificationTracker.getInstance(project),
                                               ProjectRootModificationTracker.getInstance(project));
    }).get(packageName);
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportModuleStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement resolve() {
    PsiJavaModuleReferenceElement refElement = getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    return ref != null ? ref.resolve() : null;
  }

  @Override
  public boolean isOnDemand() {
    return true;
  }

  @Override
  public String toString() {
    return "PsiImportModuleStatement";
  }
}
