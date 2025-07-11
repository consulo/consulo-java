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
package consulo.java.regexp.impl;

import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.PsiLiteralExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.util.UnicodeCharacterRegistry;
import consulo.content.bundle.Sdk;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.regexp.AsciiUtil;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.psi.*;

import java.util.Locale;
import java.util.Objects;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaRegExpHost implements RegExpLanguageHost {
    private final DefaultRegExpPropertiesProvider myPropertiesProvider;

    private final String[][] myPropertyNames = {
        {
            "Cn",
            "Unassigned"
        },
        {
            "Lu",
            "Uppercase letter"
        },
        {
            "Ll",
            "Lowercase letter"
        },
        {
            "Lt",
            "Titlecase letter"
        },
        {
            "Lm",
            "Modifier letter"
        },
        {
            "Lo",
            "Other letter"
        },
        {
            "Mn",
            "Non spacing mark"
        },
        {
            "Me",
            "Enclosing mark"
        },
        {
            "Mc",
            "Combining spacing mark"
        },
        {
            "Nd",
            "Decimal digit number"
        },
        {
            "Nl",
            "Letter number"
        },
        {
            "No",
            "Other number"
        },
        {
            "Zs",
            "Space separator"
        },
        {
            "Zl",
            "Line separator"
        },
        {
            "Zp",
            "Paragraph separator"
        },
        {
            "Cc",
            "Control"
        },
        {
            "Cf",
            "Format"
        },
        {
            "Co",
            "Private use"
        },
        {
            "Cs",
            "Surrogate"
        },
        {
            "Pd",
            "Dash punctuation"
        },
        {
            "Ps",
            "Start punctuation"
        },
        {
            "Pe",
            "End punctuation"
        },
        {
            "Pc",
            "Connector punctuation"
        },
        {
            "Po",
            "Other punctuation"
        },
        {
            "Sm",
            "Math symbol"
        },
        {
            "Sc",
            "Currency symbol"
        },
        {
            "Sk",
            "Modifier symbol"
        },
        {
            "So",
            "Other symbol"
        },
        {
            "Pi",
            "Initial quote punctuation"
        },
        {
            "Pf",
            "Final quote punctuation"
        },
        {
            "L",
            "Letter"
        },
        {
            "M",
            "Mark"
        },
        {
            "N",
            "Number"
        },
        {
            "Z",
            "Separator"
        },
        {
            "C",
            "Control"
        },
        {
            "P",
            "Punctuation"
        },
        {
            "S",
            "Symbol"
        },
        {
            "LC",
            "Letter"
        },
        {
            "LD",
            "Letter or digit"
        },
        {
            "L1",
            "Latin-1"
        },
        {
            "all",
            "All"
        },
        {
            "ASCII",
            "Ascii"
        },
        {
            "Alnum",
            "Alphanumeric characters"
        },
        {
            "Alpha",
            "Alphabetic characters"
        },
        {
            "Blank",
            "Space and tab characters"
        },
        {
            "Cntrl",
            "Control characters"
        },
        {
            "Digit",
            "Numeric characters"
        },
        {
            "Graph",
            "Printable and visible"
        },
        {
            "Lower",
            "Lowercase Alphabetic"
        },
        {
            "Print",
            "Printable characters"
        },
        {
            "Punct",
            "Punctuation characters"
        },
        {
            "Space",
            "Space characters"
        },
        {
            "Upper",
            "Uppercase alphabetic"
        },
        {
            "XDigit",
            "Hexadecimal digits"
        },
        {"javaLowerCase",},
        {"javaUpperCase",},
        {"javaTitleCase",},
        {"javaAlphabetic",},
        {"javaIdeographic",},
        {"javaDigit",},
        {"javaDefined",},
        {"javaLetter",},
        {"javaLetterOrDigit",},
        {"javaJavaIdentifierStart",},
        {"javaJavaIdentifierPart",},
        {"javaUnicodeIdentifierStart",},
        {"javaUnicodeIdentifierPart",},
        {"javaIdentifierIgnorable",},
        {"javaSpaceChar",},
        {"javaWhitespace",},
        {"javaISOControl",},
        {"javaMirrored",},
    };

    public JavaRegExpHost() {
        myPropertiesProvider = DefaultRegExpPropertiesProvider.getInstance();
    }

    @Override
    public boolean supportsInlineOptionFlag(char flag, PsiElement context) {
        switch (flag) {
            case 'i': // case-insensitive matching
            case 'd': // Unix lines mode
            case 'm': // multiline mode
            case 's': // dotall mode
            case 'u': // Unicode-aware case folding
            case 'x': // whitespace and comments in pattern
                return true;
            case 'U': // Enables the Unicode version of Predefined character classes and POSIX character classes
                return hasAtLeastJdkVersion(context, JavaSdkVersion.JDK_1_7);
            default:
                return false;
        }
    }

    @Nonnull
    @Override
    public Class getHostClass() {
        return PsiLiteralExpression.class;
    }

    @Override
    public boolean characterNeedsEscaping(char c) {
        return false;
    }

    @Override
    public boolean supportsNamedCharacters(RegExpNamedCharacter namedCharacter) {
        return hasAtLeastJdkVersion(namedCharacter, JavaSdkVersion.JDK_1_9);
    }

    @Override
    public boolean supportsPerl5EmbeddedComments() {
        return false;
    }

    @Override
    public boolean supportsPossessiveQuantifiers() {
        return true;
    }

    @Override
    public boolean supportsPythonConditionalRefs() {
        return false;
    }

    @Override
    public boolean supportsNamedGroupSyntax(RegExpGroup group) {
        return group.getType() == RegExpGroup.Type.NAMED_GROUP && hasAtLeastJdkVersion(group, JavaSdkVersion.JDK_1_7);
    }

    @Override
    public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
        return ref.isNamedGroupRef() && hasAtLeastJdkVersion(ref, JavaSdkVersion.JDK_1_7);
    }

    @Override
    public boolean isValidGroupName(String name, @Nonnull PsiElement context) {
        for (int i = 0, length = name.length(); i < length; i++) {
            final char c = name.charAt(i);
            if (!AsciiUtil.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
        return hasAtLeastJdkVersion(regExpChar, JavaSdkVersion.JDK_1_7);
    }

    @Override
    public boolean supportsBoundary(RegExpBoundary boundary) {
        switch (boundary.getType()) {
            case UNICODE_EXTENDED_GRAPHEME:
                return hasAtLeastJdkVersion(boundary, JavaSdkVersion.JDK_1_9);
            case LINE_START:
            case LINE_END:
            case WORD:
            case NON_WORD:
            case BEGIN:
            case END:
            case END_NO_LINE_TERM:
            case PREVIOUS_MATCH:
            default:
                return true;
        }
    }

    @Override
    public boolean supportsSimpleClass(RegExpSimpleClass simpleClass) {
        switch (simpleClass.getKind()) {
            case UNICODE_LINEBREAK:
            case HORIZONTAL_SPACE:
            case NON_HORIZONTAL_SPACE:
            case NON_VERTICAL_SPACE:
                return hasAtLeastJdkVersion(simpleClass, JavaSdkVersion.JDK_1_8);
            case VERTICAL_SPACE:
                // is vertical tab before jdk 1.8
                return true;
            case UNICODE_GRAPHEME:
                return hasAtLeastJdkVersion(simpleClass, JavaSdkVersion.JDK_1_9);
            case XML_NAME_START:
            case NON_XML_NAME_START:
            case XML_NAME_PART:
            case NON_XML_NAME_PART:
                return false;
            default:
                return true;
        }
    }

    @Override
    public boolean supportsLiteralBackspace(RegExpChar aChar) {
        return false;
    }

    private static boolean hasAtLeastJdkVersion(PsiElement element, JavaSdkVersion version) {
        return getJavaVersion(element).isAtLeast(version);
    }

    @Nonnull
    @RequiredReadAction
    private static JavaSdkVersion getJavaVersion(PsiElement element) {
        final Module module = element.getModule();
        if (module != null) {
            final Sdk sdk = ModuleUtilCore.getSdk(element, JavaModuleExtension.class);
            if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
                final JavaSdkVersion version = JavaSdkTypeUtil.getVersion(sdk);
                if (version != null) {
                    return version;
                }
            }
        }
        return JavaSdkVersion.MAX_JDK;
    }

    @Override
    public boolean isValidCategory(@Nonnull String category) {
        if (category.startsWith("In")) {
            return isValidUnicodeBlock(category);
        }
        if (category.startsWith("Is")) {
            category = category.substring(2);
            if (isValidProperty(category)) {
                return true;
            }

            // Unicode properties and scripts available since JDK 1.7
            category = category.toUpperCase(Locale.ENGLISH);
            switch (category) { // see java.util.regex.UnicodeProp
                // 4 aliases
                case "WHITESPACE":
                case "HEXDIGIT":
                case "NONCHARACTERCODEPOINT":
                case "JOINCONTROL":

                case "ALPHABETIC":
                case "LETTER":
                case "IDEOGRAPHIC":
                case "LOWERCASE":
                case "UPPERCASE":
                case "TITLECASE":
                case "WHITE_SPACE":
                case "CONTROL":
                case "PUNCTUATION":
                case "HEX_DIGIT":
                case "ASSIGNED":
                case "NONCHARACTER_CODE_POINT":
                case "DIGIT":
                case "ALNUM":
                case "BLANK":
                case "GRAPH":
                case "PRINT":
                case "WORD":
                case "JOIN_CONTROL":
                    return true;
            }
            return isValidUnicodeScript(category);
        }
        return isValidProperty(category);
    }

    private boolean isValidProperty(@Nonnull String category) {
        for (String[] name : myPropertyNames) {
            if (name[0].equals(category)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidUnicodeBlock(@Nonnull String category) {
        try {
            return Character.UnicodeBlock.forName(category.substring(2)) != null;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isValidUnicodeScript(@Nonnull String category) {
        try {
            return Character.UnicodeScript.forName(category) != null;
        }
        catch (IllegalArgumentException ignore) {
            return false;
        }
    }

    @Override
    public boolean isValidNamedCharacter(RegExpNamedCharacter namedCharacter) {
        return UnicodeCharacterRegistry.listCharacters().stream().filter(it -> Objects.equals(it.getName(), namedCharacter.getName())).findFirst().isPresent();
    }

    @Nonnull
    @Override
    public String[][] getAllKnownProperties() {
        return myPropertyNames;
    }

    @Nullable
    @Override
    public String getPropertyDescription(@Nullable String name) {
        if (StringUtil.isEmptyOrSpaces(name)) {
            return null;
        }
        for (String[] stringArray : myPropertyNames) {
            if (stringArray[0].equals(name)) {
                return stringArray.length > 1 ? stringArray[1] : stringArray[0];
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public String[][] getKnownCharacterClasses() {
        return myPropertiesProvider.getKnownCharacterClasses();
    }

    @Override
    public Integer getQuantifierValue(@Nonnull RegExpNumber number) {
        try {
            return Integer.valueOf(number.getUnescapedText());
        }
        catch (NumberFormatException e) {
            return null;
        }
    }
}
