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
 * @author max
 */
package com.intellij.java.language.util;

public class VisibilityIcons {
    private VisibilityIcons() {
    }

//  public static void setVisibilityIcon(PsiModifierList modifierList, RowIcon baseIcon) {
//    if (modifierList != null) {
//      if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
//        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PUBLIC, baseIcon);
//      }
//      else if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
//        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PRIVATE, baseIcon);
//      }
//      else if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
//        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PROTECTED, baseIcon);
//      }
//      else if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
//        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, baseIcon);
//      }
//      else {
//        Icon emptyIcon = new EmptyIcon(PlatformIconGroup.nodesC_public().getIconWidth(), PlatformIconGroup.nodesC_public().getIconHeight());
//        baseIcon.setIcon(emptyIcon, 1);
//      }
//    }
//    else if (PlatformIconGroup.nodesC_public() != null) {
//        Icon emptyIcon = new EmptyIcon(PlatformIconGroup.nodesC_public().getIconWidth(), PlatformIconGroup.nodesC_public().getIconHeight());
//        baseIcon.setIcon(emptyIcon, 1);
//      }
//  }
//
//  public static void setVisibilityIcon(int accessLevel, RowIcon baseIcon) {
//    Icon icon;
//    switch (accessLevel) {
//      case PsiUtil.ACCESS_LEVEL_PUBLIC:
//        icon = PlatformIconGroup.nodesC_public();
//        break;
//      case PsiUtil.ACCESS_LEVEL_PROTECTED:
//        icon = PlatformIconGroup.nodesC_protected();
//        break;
//      case PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL:
//        icon = PlatformIconGroup.nodesC_plocal();
//        break;
//      case PsiUtil.ACCESS_LEVEL_PRIVATE:
//        icon = PlatformIconGroup.nodesC_private();
//        break;
//      default:
//        if (PlatformIconGroup.nodesC_public() != null) {
//          icon = new EmptyIcon(PlatformIconGroup.nodesC_public().getIconWidth(), PlatformIconGroup.nodesC_public().getIconHeight());
//        }
//        else {
//          return;
//        }
//    }
//    baseIcon.setIcon(icon, 1);
//  }
}
