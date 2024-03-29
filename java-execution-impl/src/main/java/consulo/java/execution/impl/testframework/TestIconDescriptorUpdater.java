/*
 * Copyright 2013 must-be.org
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
package consulo.java.execution.impl.testframework;

import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 1:22/19.07.13
 */
@ExtensionImpl
public class TestIconDescriptorUpdater implements IconDescriptorUpdater {
  @RequiredReadAction
  @Override
  public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int flags) {
    for (TestFramework framework : TestFramework.EXTENSION_NAME.getExtensionList()) {
      if (framework.isIgnoredMethod(element)) {
        iconDescriptor.setMainIcon(AllIcons.RunConfigurations.IgnoredTest);
      }

      if (framework.isTestMethod(element) || framework.isTestClass(element)) {
        iconDescriptor.addLayerIcon(AllIcons.RunConfigurations.TestMark);
      }
    }
  }
}
