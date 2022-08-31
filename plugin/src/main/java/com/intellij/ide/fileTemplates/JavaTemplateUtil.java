/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.java.language.psi.PsiMethod;
import consulo.psi.PsiPackage;

import javax.annotation.Nonnull;
import java.util.Properties;

import static com.intellij.util.ObjectUtil.notNull;

/**
 * @author yole
 */
public class JavaTemplateUtil
{
	public static final String TEMPLATE_CATCH_BODY = "Catch Statement Body.java";
	public static final String TEMPLATE_SWITCH_DEFAULT_BRANCH = "Switch Default Branch.java";
	public static final String TEMPLATE_IMPLEMENTED_METHOD_BODY = "Implemented Method Body.java";
	public static final String TEMPLATE_OVERRIDDEN_METHOD_BODY = "Overridden Method Body.java";
	public static final String TEMPLATE_FROM_USAGE_METHOD_BODY = "New Method Body.java";
	public static final String TEMPLATE_I18NIZED_EXPRESSION = "I18nized Expression.java";
	public static final String TEMPLATE_I18NIZED_CONCATENATION = "I18nized Concatenation.java";
	public static final String TEMPLATE_I18NIZED_JSP_EXPRESSION = "I18nized JSP Expression.jsp";

	public static final String INTERNAL_CLASS_TEMPLATE_NAME = "Java Class";
	public static final String INTERNAL_INTERFACE_TEMPLATE_NAME = "Java Interface";
	public static final String INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME = "Java AnnotationType";
	public static final String INTERNAL_ENUM_TEMPLATE_NAME = "Java Enum";
	public static final String FILE_HEADER_TEMPLATE_NAME = "Java File Header";
	public static final String INTERNAL_RECORD_TEMPLATE_NAME = "Java Record";

	public static final String[] INTERNAL_CLASS_TEMPLATES = {
			INTERNAL_CLASS_TEMPLATE_NAME,
			INTERNAL_INTERFACE_TEMPLATE_NAME,
			INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME,
			INTERNAL_ENUM_TEMPLATE_NAME,
			INTERNAL_RECORD_TEMPLATE_NAME
	};

	public static final String INTERNAL_PACKAGE_INFO_TEMPLATE_NAME = "package-info";
	public static final String INTERNAL_MODULE_INFO_TEMPLATE_NAME = "module-info";

	public static final String[] INTERNAL_FILE_TEMPLATES = {
			INTERNAL_PACKAGE_INFO_TEMPLATE_NAME,
			INTERNAL_MODULE_INFO_TEMPLATE_NAME
	};

	private JavaTemplateUtil()
	{
	}

	public static void setClassAndMethodNameProperties(@Nonnull Properties properties, @Nonnull PsiClass aClass, @Nonnull PsiMethod method)
	{
		properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, notNull(aClass.getQualifiedName(), ""));
		properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, notNull(aClass.getName(), ""));
		properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, method.getName());
	}

	@Nonnull
	public static String getPackageName(@Nonnull PsiDirectory directory)
	{
		PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
		return aPackage != null ? aPackage.getQualifiedName() : "";
	}

	public static void setPackageNameAttribute(@Nonnull Properties properties, @Nonnull PsiDirectory directory)
	{
		properties.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, getPackageName(directory));
	}
}