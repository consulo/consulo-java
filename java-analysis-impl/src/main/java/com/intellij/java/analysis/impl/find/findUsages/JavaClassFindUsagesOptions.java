package com.intellij.java.analysis.impl.find.findUsages;

import java.util.LinkedHashSet;

import jakarta.annotation.Nonnull;
import consulo.find.FindBundle;
import consulo.project.Project;

/**
 * @author peter
 */
public class JavaClassFindUsagesOptions extends JavaFindUsagesOptions {
  public boolean isMethodsUsages = false;
  public boolean isFieldsUsages = false;
  public boolean isDerivedClasses = false;
  public boolean isImplementingClasses = false;
  public boolean isDerivedInterfaces = false;
  public boolean isCheckDeepInheritance = true;
  public boolean isIncludeInherited = false;

  public JavaClassFindUsagesOptions(@Nonnull Project project) {
    super(project);
  }

  @Override
  protected void addUsageTypes(LinkedHashSet<String> strings) {
    if (isUsages || isMethodsUsages || isFieldsUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    if (isDerivedClasses) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.classes"));
    }
    if (isImplementingClasses) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.classes"));
    }
    if (isDerivedInterfaces) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.interfaces"));
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(this)) return false;
    if (o == null || getClass() != o.getClass()) return false;

    final JavaClassFindUsagesOptions that = (JavaClassFindUsagesOptions)o;

    if (isCheckDeepInheritance != that.isCheckDeepInheritance) return false;
    if (isDerivedClasses != that.isDerivedClasses) return false;
    if (isDerivedInterfaces != that.isDerivedInterfaces) return false;
    if (isFieldsUsages != that.isFieldsUsages) return false;
    if (isImplementingClasses != that.isImplementingClasses) return false;
    if (isIncludeInherited != that.isIncludeInherited) return false;
    if (isMethodsUsages != that.isMethodsUsages) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (isMethodsUsages ? 1 : 0);
    result = 31 * result + (isFieldsUsages ? 1 : 0);
    result = 31 * result + (isDerivedClasses ? 1 : 0);
    result = 31 * result + (isImplementingClasses ? 1 : 0);
    result = 31 * result + (isDerivedInterfaces ? 1 : 0);
    result = 31 * result + (isCheckDeepInheritance ? 1 : 0);
    result = 31 * result + (isIncludeInherited ? 1 : 0);
    return result;
  }

}
