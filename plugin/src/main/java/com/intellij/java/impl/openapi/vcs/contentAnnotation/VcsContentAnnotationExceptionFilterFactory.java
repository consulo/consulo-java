/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.openapi.vcs.contentAnnotation;

import com.intellij.java.execution.filters.ExceptionFilterFactory;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.ui.console.Filter;
import consulo.language.psi.scope.GlobalSearchScope;

import javax.annotation.Nonnull;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/5/11
 * Time: 8:03 PM
 */
@ExtensionImpl
public class VcsContentAnnotationExceptionFilterFactory implements ExceptionFilterFactory {
  @Nonnull
  @Override
  public Filter create(@Nonnull GlobalSearchScope searchScope) {
    return new VcsContentAnnotationExceptionFilter(searchScope);
  }
}
