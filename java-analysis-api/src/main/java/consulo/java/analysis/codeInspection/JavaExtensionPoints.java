/*
 * Copyright 2013 Consulo.org
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
package consulo.java.analysis.codeInspection;

import com.intellij.java.analysis.codeInspection.ex.EntryPoint;
import consulo.component.extension.ExtensionPointName;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 10:38/21.05.13
 */
@Deprecated
public interface JavaExtensionPoints {
  @Nonnull
  @Deprecated
  ExtensionPointName<EntryPoint> DEAD_CODE_EP_NAME = ExtensionPointName.create(EntryPoint.class);

  @Nonnull
  ExtensionPointName<CantBeStaticCondition> CANT_BE_STATIC_EP_NAME = ExtensionPointName.create(CantBeStaticCondition.class);
}
