/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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

package com.google.devtools.j2objc.types;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.j2objc.Options;
import com.google.devtools.j2objc.util.ErrorUtil;
import com.google.devtools.j2objc.util.NameTable;

import org.eclipse.jdt.core.dom.ITypeBinding;

import java.util.Collection;
import java.util.Set;

/**
 * Description of an imported type. Imports are equal if their fully qualified
 * type names are equal.
 *
 * @author Tom Ball
 */
public class Import implements Comparable<Import> {

  private static final Set<String> FOUNDATION_TYPES =
      ImmutableSet.of("id", "NSObject", "NSString", "NSNumber", "NSCopying", "NSZone");

  private final ITypeBinding type;
  private final String typeName;
  private final String mainTypeName;
  private final String importFileName;

  /**
   * Public packages included by the j2objc libraries. This list is necessary so
   * that when package directories are suppressed, the platform headers can still
   * be found.
   */
  // TODO(tball): move this list to a distributed file, perhaps generated by build.
  private static final Set<String> PLATFORM_PACKAGES = Sets.newHashSet(new String[] {
      "android",
      "com.android.internal.util",
      "com.google.android",
      "com.google.common",
      "com.google.common.annotations",
      "com.google.common.base",
      "com.google.common.cache",
      "com.google.common.collect",
      "com.google.common.hash",
      "com.google.common.io",
      "com.google.common.math",
      "com.google.common.net",
      "com.google.common.primitives",
      "com.google.common.util",
      "com.google.j2objc",
      "com.google.protobuf",
      "dalvik",
      "java",
      "javax",
      "junit",
      "libcore",
      "org.apache.harmony",
      "org.hamcrest",
      "org.json",
      "org.junit",
      "org.kxml2",
      "org.mockito",
      "org.w3c",
      "org.xml.sax",
      "org.xmlpull",
      "sun.misc",
  });

  private Import(ITypeBinding type) {
    this.type = type;
    this.typeName = NameTable.getFullName(type);
    ITypeBinding mainType = type;
    while (!mainType.isTopLevel()) {
      mainType = mainType.getDeclaringClass();
    }
    this.mainTypeName = NameTable.getFullName(mainType);
    this.importFileName = getImportFileName(mainType);
  }

  public ITypeBinding getType() {
    return type;
  }

  public String getTypeName() {
    return typeName;
  }

  public String getMainTypeName() {
    return mainTypeName;
  }

  private static String getImportFileName(ITypeBinding type) {
    String javaName = type.getErasure().getQualifiedName();
    if (type instanceof IOSTypeBinding) {
      // Some IOS types are declared in a different header.
      String header = ((IOSTypeBinding) type).getHeader();
      if (header != null) {
        javaName = header;
      }
    }

    String mappedHeader = Options.getHeaderMappings().get(javaName);
    if (mappedHeader == null) {
      // Use package directories for platform classes if they do not have an entry in the header
      // mapping.
      if (Options.usePackageDirectories() || isPlatformClass(javaName)) {
        return javaName.replace('.', '/');
      } else {
        return javaName.substring(javaName.lastIndexOf('.') + 1);
      }
    } else {
      if (mappedHeader.substring(mappedHeader.length() - 2).equals(".h")) {
        mappedHeader = mappedHeader.substring(0, mappedHeader.length() - 2);
      } else {
        ErrorUtil.error("filename \"" + mappedHeader + "\" is not a valid header file name");
      }
      return mappedHeader;
    }
  }

  private static boolean isPlatformClass(String className) {
    String[] parts = className.split("\\.");
    String pkg = null;
    for (int i = 0; i < parts.length; i++) {
      pkg = i == 0 ? parts[0] : String.format("%s.%s", pkg, parts[i]);
      if (PLATFORM_PACKAGES.contains(pkg)) {
        return true;
      }
    }
    return false;
  }

  public String getImportFileName() {
    return importFileName;
  }

  public boolean isInterface() {
    return type.isInterface();
  }

  @Override
  public int compareTo(Import other) {
    return typeName.compareTo(other.typeName);
  }

  @Override
  public int hashCode() {
    return typeName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Import other = (Import) obj;
    return typeName.equals(other.typeName);
  }

  @Override
  public String toString() {
    return typeName;
  }

  public static Set<Import> getImports(ITypeBinding binding) {
    Set<Import> result = Sets.newLinkedHashSet();
    addImports(binding, result);
    return result;
  }

  public static void addImports(ITypeBinding binding, Collection<Import> imports) {
    if (binding == null || binding.isPrimitive()) {
      return;
    }
    if (binding instanceof PointerTypeBinding) {
      addImports(((PointerTypeBinding) binding).getPointeeType(), imports);
      return;
    }
    if (binding.isTypeVariable()) {
      for (ITypeBinding bound : binding.getTypeBounds()) {
        addImports(bound, imports);
      }
      return;
    }
    binding = Types.mapType(binding.getErasure());
    if (FOUNDATION_TYPES.contains(binding.getName())) {
      return;
    }
    imports.add(new Import(binding));
  }
}
