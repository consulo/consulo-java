/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.jam.view;

import consulo.ui.ex.DeleteProvider;
import com.intellij.jam.model.common.CommonModelElement;
import com.intellij.jam.model.common.CommonModelManager;
import consulo.dataContext.DataContext;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * @author peter
 */
public class JamDeleteProvider implements DeleteProvider {
  private final Collection<CommonModelElement> myElements;
  private final JamUserResponse myResponse;

  public JamDeleteProvider(final Collection<JamDeleteProvider> providers) {
    myElements = new HashSet<CommonModelElement>();
    JamUserResponse response = JamUserResponse.QUIET;
    for (JamDeleteProvider provider : providers) {
      myElements.addAll(provider.myElements);
      response = provider.myResponse;
    }
    myResponse = response;
  }

  public JamDeleteProvider(final JamUserResponse response, final Collection<CommonModelElement> elements) {
    myElements = elements;
    myResponse = response;
  }

  public JamDeleteProvider(final CommonModelElement element, final JamUserResponse response) {
    this(response, Collections.singletonList(element));
  }

  public void deleteElement(@jakarta.annotation.Nonnull DataContext dataContext) {
    CommonModelManager.getInstance().deleteModelElements(myElements, myResponse);
  }

  public boolean canDeleteElement(@Nonnull DataContext dataContext) {
    for (CommonModelElement element : myElements) {
      if (element != null && !element.isValid()) return false;
    }
    return true;
  }
}
