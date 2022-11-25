/*
 * Copyright 2013-2014 must-be.org
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

package consulo.java.impl.module.extension;

import consulo.content.ContentFolderTypeProvider;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.ProductionResourceContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.content.TestResourceContentFolderTypeProvider;
import consulo.module.content.layer.ContentFolderSupportPatcher;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.extension.ModuleExtension;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * @author VISTALL
 * @since 05.05.14
 */
public class JavaContentFolderSupportPatcher implements ContentFolderSupportPatcher {
  @Override
  public void patch(@Nonnull ModifiableRootModel model, @Nonnull Set<ContentFolderTypeProvider> set) {
    ModuleExtension javaModuleExtension = model.getExtension("java");
    if (javaModuleExtension != null) {
      set.add(ProductionContentFolderTypeProvider.getInstance());
      set.add(ProductionResourceContentFolderTypeProvider.getInstance());
      set.add(TestContentFolderTypeProvider.getInstance());
      set.add(TestResourceContentFolderTypeProvider.getInstance());
    }
  }
}
