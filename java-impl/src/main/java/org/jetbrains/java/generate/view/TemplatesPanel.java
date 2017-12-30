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
package org.jetbrains.java.generate.view;

import gnu.trove.Equality;

import java.util.ArrayList;
import java.util.Collections;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedItemsListEditor;
import com.intellij.openapi.ui.Namer;
import com.intellij.openapi.util.Cloner;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiType;

public class TemplatesPanel extends NamedItemsListEditor<TemplateResource>
{
	private static final Namer<TemplateResource> NAMER = new Namer<TemplateResource>()
	{
		@Override
		public String getName(TemplateResource templateResource)
		{
			return templateResource.getFileName();
		}

		@Override
		public boolean canRename(TemplateResource item)
		{
			return !item.isDefault();
		}

		@Override
		public void setName(TemplateResource templateResource, String name)
		{
			templateResource.setFileName(name);
		}
	};

	private static final Factory<TemplateResource> FACTORY = new Factory<TemplateResource>()
	{
		@Override
		public TemplateResource create()
		{
			return new TemplateResource();
		}
	};

	private static final Cloner<TemplateResource> CLONER = new Cloner<TemplateResource>()
	{
		@Override
		public TemplateResource cloneOf(TemplateResource templateResource)
		{
			if(templateResource.isDefault())
			{
				return templateResource;
			}
			return copyOf(templateResource);
		}

		@Override
		public TemplateResource copyOf(TemplateResource templateResource)
		{
			TemplateResource result = new TemplateResource();
			result.setFileName(templateResource.getFileName());
			result.setTemplate(templateResource.getTemplate());
			return result;
		}
	};

	private static final Equality<TemplateResource> COMPARER = new Equality<TemplateResource>()
	{
		@Override
		public boolean equals(TemplateResource o1, TemplateResource o2)
		{
			return Comparing.equal(o1.getTemplate(), o2.getTemplate()) && Comparing.equal(o1.getFileName(), o2.getFileName());
		}
	};
	private final Project myProject;
	private final TemplatesManager myTemplatesManager;

	public TemplatesPanel(Project project)
	{
		this(project, ToStringTemplatesManager.getInstance());
	}

	public TemplatesPanel(Project project, TemplatesManager templatesManager)
	{
		super(NAMER, FACTORY, CLONER, COMPARER, new ArrayList<TemplateResource>(templatesManager.getAllTemplates()));

		//ServiceManager.getService(project, MasterDetailsStateService.class).register("ToStringTemplates.UI", this);
		myProject = project;
		myTemplatesManager = templatesManager;
	}

	@Override
	@Nls
	public String getDisplayName()
	{
		return "Templates";
	}

	@Override
	protected String subjDisplayName()
	{
		return "template";
	}

	@Override
	@Nullable
	@NonNls
	public String getHelpTopic()
	{
		return "Templates Dialog";
	}

	@Override
	public boolean isModified()
	{
		return super.isModified() || !Comparing.equal(myTemplatesManager.getDefaultTemplate(), getSelectedItem());
	}

	@Override
	protected boolean canDelete(TemplateResource item)
	{
		return !item.isDefault();
	}

	@Override
	protected UnnamedConfigurable createConfigurable(TemplateResource item)
	{
		return new GenerateTemplateConfigurable(item, Collections.<String, PsiType>emptyMap(), myProject, onMultipleFields());
	}

	protected boolean onMultipleFields()
	{
		return true;
	}

	@Override
	public void apply() throws ConfigurationException
	{
		super.apply();
		myTemplatesManager.setTemplates(getItems());
		final TemplateResource selection = getSelectedItem();
		if(selection != null)
		{
			myTemplatesManager.setDefaultTemplate(selection);
		}
	}
}