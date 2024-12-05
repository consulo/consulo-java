/*
 * Copyright 2013-2018 consulo.io
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
package consulo.java.execution.projectRoots;

import com.intellij.java.execution.CommandLineWrapperUtil;
import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTypeId;
import consulo.execution.CantRunException;
import consulo.execution.localize.ExecutionLocalize;
import consulo.java.execution.OwnSimpleJavaParameters;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParametersListUtil;
import consulo.process.local.OSProcessUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.ClassPathUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.nodep.classloader.UrlClassLoader;
import consulo.util.nodep.text.StringUtilRt;
import consulo.virtualFileSystem.encoding.ApplicationEncodingManager;
import consulo.virtualFileSystem.util.PathsList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.Manifest;

public class OwnJdkUtil {
    private static final String WRAPPER_CLASS = "com.intellij.rt.execution.CommandLineWrapper";
    public static final Key<Map<String, String>> COMMAND_LINE_CONTENT = Key.create("command.line.content");
    /**
     * The VM property is needed to workaround incorrect escaped URLs handling in WebSphere,
     * see <a href="https://youtrack.jetbrains.com/issue/IDEA-126859#comment=27-778948">IDEA-126859</a> for additional details
     */
    public static final String PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL = "idea.do.not.escape.classpath.url";

    public static boolean checkForJdk(@Nonnull String homePath) {
        return checkForJdk(new File(FileUtil.toSystemDependentName(homePath)));
    }

    public static boolean checkForJdk(@Nonnull File homePath) {
        return (new File(homePath, "bin/javac").isFile() || new File(
            homePath,
            "bin/javac.exe"
        ).isFile()) && checkForRuntime(homePath.getAbsolutePath());
    }

    public static boolean checkForJre(@Nonnull String homePath) {
        return checkForJre(new File(FileUtil.toSystemDependentName(homePath)));
    }

    public static boolean checkForJre(@Nonnull File homePath) {
        return new File(homePath, "bin/java").isFile() || new File(homePath, "bin/java.exe").isFile();
    }

    public static boolean checkForRuntime(@Nonnull String homePath) {
        return new File(homePath, "jre/lib/rt.jar").exists()          // JDK
            || new File(homePath, "lib/rt.jar").exists()              // JRE
            || isModularRuntime(homePath)                             // Jigsaw JDK/JRE
            || new File(homePath, "../Classes/classes.jar").exists()  // Apple JDK
            || new File(homePath, "jre/lib/vm.jar").exists()          // IBM JDK
            || new File(homePath, "classes").isDirectory();           // custom build
    }

    public static boolean isModularRuntime(@Nonnull String homePath) {
        return isModularRuntime(new File(FileUtil.toSystemDependentName(homePath)));
    }

    public static boolean isModularRuntime(@Nonnull File homePath) {
        return new File(homePath, "lib/jrt-fs.jar").isFile() || isExplodedModularRuntime(homePath.getPath());
    }

    public static boolean isExplodedModularRuntime(@Nonnull String homePath) {
        return new File(homePath, "modules/java.base").isDirectory();
    }

    public static GeneralCommandLine setupJVMCommandLine(@Nonnull OwnSimpleJavaParameters javaParameters) throws CantRunException {
        Sdk jdk = javaParameters.getJdk();
        if (jdk == null) {
            throw new CantRunException(ExecutionLocalize.runConfigurationErrorNoJdkSpecified().get());
        }
        SdkTypeId type = jdk.getSdkType();
        if (!(type instanceof JavaSdkType)) {
            throw new CantRunException(ExecutionLocalize.runConfigurationErrorNoJdkSpecified().get());
        }

        GeneralCommandLine commandLine = new GeneralCommandLine();
        ((JavaSdkType)type).setupCommandLine(commandLine, jdk);
        String exePath = commandLine.getExePath();
        if (exePath == null) {
            throw new CantRunException(ExecutionLocalize.runConfigurationCannotFindVmExecutable().get());
        }

        setupCommandLine(commandLine, javaParameters);
        return commandLine;
    }

    private static void setupCommandLine(GeneralCommandLine commandLine, OwnSimpleJavaParameters javaParameters) throws CantRunException {
        commandLine.withWorkDirectory(javaParameters.getWorkingDirectory());

        commandLine.withEnvironment(javaParameters.getEnv());
        commandLine.withParentEnvironmentType(javaParameters.isPassParentEnvs()
            ? GeneralCommandLine.ParentEnvironmentType.CONSOLE
            : GeneralCommandLine.ParentEnvironmentType.NONE);

        ParametersList vmParameters = javaParameters.getVMParametersList();
        boolean dynamicClasspath = javaParameters.isDynamicClasspath();
        boolean dynamicVMOptions = dynamicClasspath && javaParameters.isDynamicVMOptions();
        boolean dynamicParameters = dynamicClasspath && javaParameters.isDynamicParameters();
        boolean dynamicMainClass = false;

        if (dynamicClasspath) {
            Class commandLineWrapper;
            if (javaParameters.isArgFile()) {
                setArgFileParams(commandLine, javaParameters, vmParameters, dynamicVMOptions, dynamicParameters);
                dynamicMainClass = dynamicParameters;
            }
            else if (!explicitClassPath(vmParameters) && javaParameters.getJarPath() == null && (commandLineWrapper =
                getCommandLineWrapperClass()) != null) {
                if (javaParameters.isUseClasspathJar()) {
                    setClasspathJarParams(
                        commandLine,
                        javaParameters,
                        vmParameters,
                        commandLineWrapper,
                        dynamicVMOptions,
                        dynamicParameters
                    );
                }
                else if (javaParameters.isClasspathFile()) {
                    setCommandLineWrapperParams(
                        commandLine,
                        javaParameters,
                        vmParameters,
                        commandLineWrapper,
                        dynamicVMOptions,
                        dynamicParameters
                    );
                }
            }
            else {
                dynamicClasspath = dynamicParameters = false;
            }
        }

        if (!dynamicClasspath) {
            appendParamsEncodingClasspath(javaParameters, commandLine, vmParameters);
        }

        if (!dynamicMainClass) {
            commandLine.addParameters(getMainClassParams(javaParameters));
        }

        if (!dynamicParameters) {
            commandLine.addParameters(javaParameters.getProgramParametersList().getList());
        }
    }

    private static boolean explicitClassPath(ParametersList vmParameters) {
        return vmParameters.hasParameter("-cp") || vmParameters.hasParameter("-classpath") || vmParameters.hasParameter("--class-path");
    }

    private static boolean explicitModulePath(ParametersList vmParameters) {
        return vmParameters.hasParameter("-p") || vmParameters.hasParameter("--module-path");
    }

    private static void setCommandLineWrapperParams(
        GeneralCommandLine commandLine,
        OwnSimpleJavaParameters javaParameters,
        ParametersList vmParameters,
        Class commandLineWrapper,
        boolean dynamicVMOptions,
        boolean dynamicParameters
    ) throws CantRunException {
        try {
            File vmParamsFile = null;
            if (dynamicVMOptions) {
                vmParamsFile = FileUtil.createTempFile("idea_vm_params", null);
                try (PrintWriter writer = new PrintWriter(vmParamsFile)) {
                    for (String param : vmParameters.getList()) {
                        if (isUserDefinedProperty(param)) {
                            writer.println(param);
                        }
                        else {
                            commandLine.addParameter(param);
                        }
                    }
                }
            }
            else {
                commandLine.addParameters(vmParameters.getList());
            }

            appendEncoding(javaParameters, commandLine, vmParameters);

            File appParamsFile = null;
            if (dynamicParameters) {
                appParamsFile = FileUtil.createTempFile("idea_app_params", null);
                try (PrintWriter writer = new PrintWriter(appParamsFile)) {
                    for (String parameter : javaParameters.getProgramParametersList().getList()) {
                        writer.println(parameter);
                    }
                }
            }

            File classpathFile = FileUtil.createTempFile("idea_classpath", null);
            PathsList classPath = javaParameters.getClassPath();
            try (PrintWriter writer = new PrintWriter(classpathFile)) {
                for (String path : classPath.getPathList()) {
                    writer.println(path);
                }
            }

            Map<String, String> map = Map.of(classpathFile.getAbsolutePath(), classPath.getPathsString());
            commandLine.putUserData(COMMAND_LINE_CONTENT, map);

            Set<String> classpath = new LinkedHashSet<>();
            classpath.add(ClassPathUtil.getJarPathForClass(commandLineWrapper));
            if (UrlClassLoader.class.getName().equals(vmParameters.getPropertyValue("java.system.class.loader"))) {
                classpath.add(ClassPathUtil.getJarPathForClass(UrlClassLoader.class));
                classpath.add(ClassPathUtil.getJarPathForClass(StringUtilRt.class));
                //classpath.add(ClassPathUtil.getJarPathForClass(THashMap.class));
            }
            commandLine.addParameter("-classpath");
            commandLine.addParameter(StringUtil.join(classpath, File.pathSeparator));

            commandLine.addParameter(commandLineWrapper.getName());
            commandLine.addParameter(classpathFile.getAbsolutePath());
            OSProcessUtil.deleteFileOnTermination(commandLine, classpathFile);

            if (vmParamsFile != null) {
                commandLine.addParameter("@vm_params");
                commandLine.addParameter(vmParamsFile.getAbsolutePath());
                map.put(vmParamsFile.getAbsolutePath(), Files.readString(vmParamsFile.toPath()));
                OSProcessUtil.deleteFileOnTermination(commandLine, vmParamsFile);
            }

            if (appParamsFile != null) {
                commandLine.addParameter("@app_params");
                commandLine.addParameter(appParamsFile.getAbsolutePath());
                map.put(appParamsFile.getAbsolutePath(), Files.readString(appParamsFile.toPath()));
                OSProcessUtil.deleteFileOnTermination(commandLine, appParamsFile);
            }
        }
        catch (IOException e) {
            throwUnableToCreateTempFile(e);
        }
    }

    private static void setArgFileParams(
        GeneralCommandLine commandLine,
        OwnSimpleJavaParameters javaParameters,
        ParametersList vmParameters,
        boolean dynamicVMOptions,
        boolean dynamicParameters
    ) throws CantRunException {
        try {
            File argFile = FileUtil.createTempFile("consulo_arg_file", null);

            try (PrintWriter writer = new PrintWriter(argFile)) {
                if (dynamicVMOptions) {
                    for (String param : vmParameters.getList()) {
                        writer.print(quoteArg(param));
                        writer.print('\n');
                    }
                }
                else {
                    commandLine.addParameters(vmParameters.getList());
                }

                PathsList classPath = javaParameters.getClassPath();
                if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
                    writer.print("-classpath\n");
                    writer.print(quoteArg(classPath.getPathsString()));
                    writer.print('\n');
                }

                PathsList modulePath = javaParameters.getModulePath();
                if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
                    writer.print("-p\n");
                    writer.print(quoteArg(modulePath.getPathsString()));
                    writer.print('\n');
                }

                if (dynamicParameters) {
                    for (String parameter : getMainClassParams(javaParameters)) {
                        writer.print(quoteArg(parameter));
                        writer.print('\n');
                    }
                    for (String parameter : javaParameters.getProgramParametersList().getList()) {
                        writer.print(quoteArg(parameter));
                        writer.print('\n');
                    }
                }
            }

            commandLine.putUserData(COMMAND_LINE_CONTENT, Map.of(argFile.getAbsolutePath(), Files.readString(argFile.toPath())));

            appendEncoding(javaParameters, commandLine, vmParameters);

            commandLine.addParameter("@" + argFile.getAbsolutePath());

            OSProcessUtil.deleteFileOnTermination(commandLine, argFile);
        }
        catch (IOException e) {
            throwUnableToCreateTempFile(e);
        }
    }

    private static void appendParamsEncodingClasspath(
        OwnSimpleJavaParameters javaParameters,
        GeneralCommandLine commandLine,
        ParametersList vmParameters
    ) {
        commandLine.addParameters(vmParameters.getList());

        appendEncoding(javaParameters, commandLine, vmParameters);

        PathsList classPath = javaParameters.getClassPath();
        if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
            commandLine.addParameter("-classpath");
            commandLine.addParameter(classPath.getPathsString());
        }

        PathsList modulePath = javaParameters.getModulePath();
        if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
            commandLine.addParameter("-p");
            commandLine.addParameter(modulePath.getPathsString());
        }
    }

    private static void appendEncoding(
        OwnSimpleJavaParameters javaParameters,
        GeneralCommandLine commandLine,
        ParametersList parametersList
    ) {
        // Value of file.encoding and charset of GeneralCommandLine should be in sync in order process's input and output be correctly handled.
        String encoding = parametersList.getPropertyValue("file.encoding");
        if (encoding == null) {
            Charset charset = javaParameters.getCharset();
            if (charset == null) {
                charset = ApplicationEncodingManager.getInstance().getDefaultCharset();
            }
            commandLine.addParameter("-Dfile.encoding=" + charset.name());
            commandLine.withCharset(charset);
        }
        else {
            try {
                Charset charset = Charset.forName(encoding);
                commandLine.withCharset(charset);
            }
            catch (UnsupportedCharsetException | IllegalCharsetNameException ignore) {
            }
        }

        if (!parametersList.hasProperty("sun.stdout.encoding")
            && !parametersList.hasProperty("sun.stderr.encoding")) {
            try {
                Sdk jdk = javaParameters.getJdk();
                JavaSdkVersion version = jdk != null ? JavaSdkTypeUtil.getVersion(jdk) : null;
                if (version != null && version.isAtLeast(JavaSdkVersion.JDK_18)) {
                    Charset charset = javaParameters.getCharset();
                    if (charset == null) {
                        charset = ApplicationEncodingManager.getInstance().getDefaultCharset();
                    }
                    commandLine.addParameter("-Dsun.stdout.encoding=" + charset.name());
                    commandLine.addParameter("-Dsun.stderr.encoding=" + charset.name());
                }
            }
            catch (IllegalArgumentException ignore) {
            }
        }
    }

    private static List<String> getMainClassParams(OwnSimpleJavaParameters javaParameters) throws CantRunException {
        String mainClass = javaParameters.getMainClass();
        String moduleName = javaParameters.getModuleName();
        String jarPath = javaParameters.getJarPath();
        if (mainClass != null && moduleName != null) {
            return Arrays.asList("-m", moduleName + '/' + mainClass);
        }
        else if (mainClass != null) {
            return Collections.singletonList(mainClass);
        }
        else if (jarPath != null) {
            return Arrays.asList("-jar", jarPath);
        }
        else {
            throw new CantRunException(ExecutionLocalize.mainClassIsNotSpecifiedErrorMessage().get());
        }
    }

    private static void setClasspathJarParams(
        GeneralCommandLine commandLine,
        OwnSimpleJavaParameters javaParameters,
        ParametersList vmParameters,
        Class commandLineWrapper,
        boolean dynamicVMOptions,
        boolean dynamicParameters
    ) throws CantRunException {
        try {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().putValue("Created-By", Application.get().getName().get());

            String manifestText = "";
            if (dynamicVMOptions) {
                List<String> properties = new ArrayList<>();
                for (String param : vmParameters.getList()) {
                    if (isUserDefinedProperty(param)) {
                        properties.add(param);
                    }
                    else {
                        commandLine.addParameter(param);
                    }
                }
                manifest.getMainAttributes().putValue("VM-Options", ParametersListUtil.join(properties));
                manifestText += "VM-Options: " + ParametersListUtil.join(properties) + "\n";
            }
            else {
                commandLine.addParameters(vmParameters.getList());
            }

            appendEncoding(javaParameters, commandLine, vmParameters);

            if (dynamicParameters) {
                manifest.getMainAttributes()
                    .putValue("Program-Parameters", ParametersListUtil.join(javaParameters.getProgramParametersList().getList()));
                manifestText +=
                    "Program-Parameters: " + ParametersListUtil.join(javaParameters.getProgramParametersList().getList()) + "\n";
            }

            boolean notEscape = vmParameters.hasParameter(PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL);
            PathsList path = javaParameters.getClassPath();
            File classpathJarFile = CommandLineWrapperUtil.createClasspathJarFile(manifest, path.getPathList(), notEscape);

            String jarFilePath = classpathJarFile.getAbsolutePath();
            commandLine.addParameter("-classpath");
            if (dynamicVMOptions || dynamicParameters) {
                commandLine.addParameter(ClassPathUtil.getJarPathForClass(commandLineWrapper) + File.pathSeparator + jarFilePath);
                commandLine.addParameter(commandLineWrapper.getName());
            }
            commandLine.addParameter(jarFilePath);

            commandLine.putUserData(COMMAND_LINE_CONTENT, Map.of(jarFilePath, manifestText + "Class-Path: " + path.getPathsString()));

            OSProcessUtil.deleteFileOnTermination(commandLine, classpathJarFile);
        }
        catch (IOException e) {
            throwUnableToCreateTempFile(e);
        }
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static boolean isUserDefinedProperty(String param) {
        return param.startsWith("-D") && !(param.startsWith("-Dsun.") || param.startsWith("-Djava."));
    }

    private static void throwUnableToCreateTempFile(IOException cause) throws CantRunException {
        throw new CantRunException("Failed to a create temporary file in " + FileUtil.getTempDirectory(), cause);
    }

    @Nullable
    private static Class getCommandLineWrapperClass() {
        try {
            return Class.forName(WRAPPER_CLASS);
        }
        catch (ClassNotFoundException e) {
            return null;
        }
    }

    /* https://docs.oracle.com/javase/9/tools/java.htm, "java Command-Line Argument Files" */
    private static String quoteArg(String arg) {
        if (StringUtil.containsAnyChar(arg, " \"\n\r\t\f") || arg.endsWith("\\") || arg.startsWith("#")) {
            StringBuilder sb = new StringBuilder(arg.length() * 2);
            sb.append('"');

            for (int i = 0; i < arg.length(); i++) {
                char c = arg.charAt(i);
                switch (c) {
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    case '\"':
                        sb.append("\\\"");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        sb.append(c);
                }
            }

            sb.append('"');
            return sb.toString();
        }

        return arg;
    }
}
