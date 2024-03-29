package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.util.lang.PatternUtil;
import consulo.application.util.function.Processor;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class PatternPackageReferenceSet extends PackageReferenceSet {
  public PatternPackageReferenceSet(String packageName, PsiElement element, int startInElement) {
    super(packageName, element, startInElement);
  }

  @Override
  public Collection<PsiJavaPackage> resolvePackageName(@Nullable final PsiJavaPackage context, final String packageName) {
    if (context == null) return Collections.emptySet();

    if (packageName.contains("*")) {
      final Pattern pattern = PatternUtil.fromMask(packageName);
      final Set<PsiJavaPackage> packages = new HashSet<PsiJavaPackage>();

      processSubPackages(context, new Processor<PsiJavaPackage>() {
        @Override
        public boolean process(PsiJavaPackage psiPackage) {
          String name = psiPackage.getName();
          if (name != null && pattern.matcher(name).matches()) {
            packages.add(psiPackage);
          }
          return true;
        }
      });

      return packages;
    }
    else {
      return super.resolvePackageName(context, packageName);
    }
  }

   protected static boolean processSubPackages(final PsiJavaPackage pkg, final Processor<PsiJavaPackage> processor) {
    if (!processor.process(pkg)) return false;
    for (final PsiJavaPackage aPackage : pkg.getSubPackages()) {
      if (!processSubPackages(aPackage, processor)) return false;
    }
    return true;
  }
}
