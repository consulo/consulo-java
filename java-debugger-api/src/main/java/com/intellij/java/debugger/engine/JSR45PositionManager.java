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
package com.intellij.java.debugger.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.NoDataException;
import com.intellij.java.debugger.PositionManager;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.requests.ClassPrepareRequestor;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.language.file.LanguageFileType;
import consulo.application.util.function.Computable;
import consulo.language.psi.PsiFile;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.ClassNotPreparedException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.request.ClassPrepareRequest;

/**
 * @author Eugene Zhuravlev
 *         Date: May 23, 2006
 */
public abstract class JSR45PositionManager<Scope> implements PositionManager
{
	private static final Logger LOG = Logger.getInstance(JSR45PositionManager.class);
	protected final DebugProcess myDebugProcess;
	protected final Scope myScope;
	private final String myStratumId;
	protected final SourcesFinder<Scope> mySourcesFinder;
	protected final String GENERATED_CLASS_PATTERN;
	protected Matcher myGeneratedClassPatternMatcher;
	private final Set<LanguageFileType> myFileTypes;

	public JSR45PositionManager(DebugProcess debugProcess, Scope scope, final String stratumId, final LanguageFileType[] acceptedFileTypes, final SourcesFinder<Scope> sourcesFinder)
	{
		myDebugProcess = debugProcess;
		myScope = scope;
		myStratumId = stratumId;
		myFileTypes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(acceptedFileTypes)));
		mySourcesFinder = sourcesFinder;
		String generatedClassPattern = getGeneratedClassesPackage();
		if(generatedClassPattern.length() == 0)
		{
			generatedClassPattern = getGeneratedClassesNamePattern();
		}
		else
		{
			generatedClassPattern = generatedClassPattern + "." + getGeneratedClassesNamePattern();
		}
		GENERATED_CLASS_PATTERN = generatedClassPattern;
		myGeneratedClassPatternMatcher = Pattern.compile(generatedClassPattern.replaceAll("\\*", ".*")).matcher("");
	}

	@NonNls
	protected abstract String getGeneratedClassesPackage();

	protected String getGeneratedClassesNamePattern()
	{
		return "*";
	}

	public final String getStratumId()
	{
		return myStratumId;
	}

	@Override
	public SourcePosition getSourcePosition(final Location location) throws NoDataException
	{
		SourcePosition sourcePosition = null;

		try
		{
			String sourcePath = getRelativeSourcePathByLocation(location);
			PsiFile file = mySourcesFinder.findSourceFile(sourcePath, myDebugProcess.getProject(), myScope);
			if(file != null)
			{
				int lineNumber = getLineNumber(location);
				sourcePosition = SourcePosition.createFromLine(file, lineNumber - 1);
			}
		}
		catch(AbsentInformationException ignored)
		{ // ignored
		}
		catch(Throwable e)
		{
			LOG.info(e);
		}
		if(sourcePosition == null)
		{
			throw NoDataException.INSTANCE;
		}
		return sourcePosition;
	}

	protected String getRelativeSourcePathByLocation(final Location location) throws AbsentInformationException
	{
		return getRelativePath(location.sourcePath(myStratumId));
	}

	protected int getLineNumber(final Location location)
	{
		return location.lineNumber(myStratumId);
	}

	@Override
	@Nonnull
	public List<ReferenceType> getAllClasses(@Nonnull SourcePosition classPosition) throws NoDataException
	{
		checkSourcePositionFileType(classPosition);

		final List<ReferenceType> referenceTypes = myDebugProcess.getVirtualMachineProxy().allClasses();

		final List<ReferenceType> result = new ArrayList<>();

		for(final ReferenceType referenceType : referenceTypes)
		{
			myGeneratedClassPatternMatcher.reset(referenceType.name());
			if(myGeneratedClassPatternMatcher.matches())
			{
				final List<Location> locations = locationsOfClassAt(referenceType, classPosition);
				if(locations != null && locations.size() > 0)
				{
					result.add(referenceType);
				}
			}
		}

		return result;
	}

	@Nonnull
	@Override
	public Set<LanguageFileType> getAcceptedFileTypes()
	{
		return myFileTypes;
	}

	private void checkSourcePositionFileType(final SourcePosition classPosition) throws NoDataException
	{
		final FileType fileType = classPosition.getFile().getFileType();
		if(!myFileTypes.contains(fileType))
		{
			throw NoDataException.INSTANCE;
		}
	}

	@Override
	@Nonnull
	public List<Location> locationsOfLine(@Nonnull final ReferenceType type, @Nonnull final SourcePosition position) throws NoDataException
	{
		List<Location> locations = locationsOfClassAt(type, position);
		return locations != null ? locations : Collections.emptyList();

	}

	private List<Location> locationsOfClassAt(final ReferenceType type, final SourcePosition position) throws NoDataException
	{
		checkSourcePositionFileType(position);

		return ApplicationManager.getApplication().runReadAction(new Computable<List<Location>>()
		{
			@Override
			public List<Location> compute()
			{
				try
				{
					final List<String> relativePaths = getRelativeSourePathsByType(type);
					for(String relativePath : relativePaths)
					{
						final PsiFile file = mySourcesFinder.findSourceFile(relativePath, myDebugProcess.getProject(), myScope);
						if(file != null && file.equals(position.getFile()))
						{
							return getLocationsOfLine(type, getSourceName(file.getName(), type), relativePath, position.getLine() + 1);
						}
					}
				}
				catch(ObjectCollectedException | ClassNotPreparedException | AbsentInformationException ignored)
				{
				}
				catch(InternalError ignored)
				{
					myDebugProcess.printToConsole(DebuggerBundle.message("internal.error.locations.of.line", type.name()));
				}
				return null;
			}

			// Finds exact server file name (from available in type)
			// This is needed because some servers (e.g. WebSphere) put not exact file name such as 'A.jsp  '
			private String getSourceName(final String name, final ReferenceType type) throws AbsentInformationException
			{
				for(String sourceNameFromType : type.sourceNames(myStratumId))
				{
					if(sourceNameFromType.contains(name))
					{
						return sourceNameFromType;
					}
				}
				return name;
			}
		});
	}

	protected List<String> getRelativeSourePathsByType(final ReferenceType type) throws AbsentInformationException
	{
		return type.sourcePaths(myStratumId).stream().map(this::getRelativePath).collect(Collectors.toList());
	}

	protected List<Location> getLocationsOfLine(final ReferenceType type, final String fileName, final String relativePath, final int lineNumber) throws AbsentInformationException
	{
		return type.locationsOfLine(myStratumId, fileName, lineNumber);
	}

	@Override
	public ClassPrepareRequest createPrepareRequest(@Nonnull final ClassPrepareRequestor requestor, @Nonnull final SourcePosition position) throws NoDataException
	{
		checkSourcePositionFileType(position);

		return myDebugProcess.getRequestsManager().createClassPrepareRequest(new ClassPrepareRequestor()
		{
			@Override
			public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType)
			{
				onClassPrepare(debuggerProcess, referenceType, position, requestor);
			}
		}, GENERATED_CLASS_PATTERN);
	}

	protected void onClassPrepare(final DebugProcess debuggerProcess, final ReferenceType referenceType, final SourcePosition position, final ClassPrepareRequestor requestor)
	{
		try
		{
			if(locationsOfClassAt(referenceType, position) != null)
			{
				requestor.processClassPrepare(debuggerProcess, referenceType);
			}
		}
		catch(NoDataException ignored)
		{
		}
	}

	protected String getRelativePath(String sourcePath)
	{

		if(sourcePath != null)
		{
			sourcePath = sourcePath.trim();
			String generatedClassesPackage = getGeneratedClassesPackage();
			final String prefix = generatedClassesPackage.replace('.', File.separatorChar);

			if(sourcePath.startsWith(prefix))
			{
				sourcePath = sourcePath.substring(prefix.length());
				if(sourcePath.startsWith(File.separator))
				{
					sourcePath = sourcePath.substring(1);
				}
			}
		}

		return sourcePath;
	}

}
