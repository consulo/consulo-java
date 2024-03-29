/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * User: anna
 * Date: 19-Aug-2008
 */
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;

@ExtensionImpl
public class OverrideImplementsAnnotationsHandlerImpl implements OverrideImplementsAnnotationsHandler {
  @Override
  public String[] getAnnotations(Project project) {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    final Collection<String> anns = new ArrayList<String>(manager.getNotNulls());
    anns.addAll(manager.getNullables());
    anns.add(AnnotationUtil.NLS);
    return ArrayUtil.toStringArray(anns);
  }

  @Override
  @Nonnull
  public String[] annotationsToRemove(Project project, @Nonnull final String fqName) {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    if (manager.getNotNulls().contains(fqName)) {
      return ArrayUtil.toStringArray(manager.getNullables());
    }
    if (manager.getNullables().contains(fqName)) {
      return ArrayUtil.toStringArray(manager.getNotNulls()); 
    }
    if (Comparing.strEqual(fqName, AnnotationUtil.NLS)){
      return new String[]{AnnotationUtil.NON_NLS};
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }
}
