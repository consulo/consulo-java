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
package com.intellij.jam.model.common;

import com.intellij.jam.view.DeleteHandler;
import com.intellij.jam.view.JamDeleteHandler;
import com.intellij.jam.view.JamUserResponse;
import consulo.ide.ServiceManager;
import consulo.xml.util.xml.DomElement;

import jakarta.annotation.Nullable;
import java.util.Collection;

/**
 * @author peter
 */
public abstract class CommonModelManager {
  public static CommonModelManager getInstance() {
    return ServiceManager.getService(CommonModelManager.class);
  }

  public abstract void deleteModelElement(CommonModelElement element, JamUserResponse response);

  /**
   * @deprecated
   * @see DeleteHandler
   */
  public abstract void registerDeleteHandler(JamDeleteHandler handler);

  @Nullable
  public abstract <T extends DomElement> T getDomElement(CommonModelElement element);

  public abstract void deleteModelElements(Collection<? extends CommonModelElement> elements, JamUserResponse response);

}
