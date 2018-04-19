/*
 * Copyright (C) 2018 Marco Herrn
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
package org.omnaest.i18nbinder.facade.creation;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import javax.annotation.Generated;
import javax.lang.model.element.Modifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.omnaest.i18nbinder.internal.Translation;
import org.omnaest.i18nbinder.facade.I18nBundleKey;


/**
 * This class allow the creation of the I18n Facade(s) for java localization resource
 * bundles.
 *
 * @author mherrn
 */
//TODO: Write unit tests for this class
public class FacadeCreator {
  private static final Logger LOGGER= LogManager.getLogger();


  /////////////////////////////////////////////////////////////////////////////
  //
  // Attributes

  /////////////////////////////////////////////////////////////////////////////
  //
  // Constructors

  /////////////////////////////////////////////////////////////////////////////
  //
  // Methods

  /**
   * Creates an enum class for the given resource bundle.
   *
   * @param resourceContent the resource bundle for which to create the enum class.
   * @return the created enum class TypeSpec
   */
  public TypeSpec createFacadeEnumFor(final FacadeBundleContent resourceContent) {
    final String enumName= this.getEnumClassNameFor(resourceContent);

    // build an enum class for the resource bundle
    final TypeSpec.Builder bundleEnumBuilder= TypeSpec.enumBuilder(enumName)
            .addAnnotation(AnnotationSpec.builder(Generated.class)
              .addMember("value", "$S", this.getClass().getName())
              .addMember("date", "$S", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
              .build())
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ClassName.get(I18nBundleKey.class))
            // BASENAME of the bundle as static const
            .addField(FieldSpec.builder(String.class, "BASENAME", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", resourceContent.getBundleBaseName())
                    .build())
            // the resource key as a field of each enum constant
            .addField(String.class, "key", Modifier.PRIVATE, Modifier.FINAL)
            // add a constructor to initialize the resource key
            .addMethod(MethodSpec.constructorBuilder()
                    .addParameter(String.class, "key")
                    .addStatement("this.$N = $N", "key", "key")
                    .build())
            // override getBasename() to return the resource bundle basename
            .addMethod(MethodSpec.methodBuilder("getBasename")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(String.class)
                    .addStatement("return $N", "BASENAME")
                    .build())
            //.addMethod(MethodSpec.overriding(I18nBundleKey.class.getMethod("getBasename")))
            .addMethod(MethodSpec.methodBuilder("getKey")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(String.class)
                    .addStatement("return this.$N", "key")
                    .build());

    // now add the actual enum constants
    resourceContent.getContent().asMap().forEach((key, translations) -> {
      bundleEnumBuilder.addEnumConstant(toEnumConstName(key),
                                        TypeSpec.anonymousClassBuilder("$S", key)
                                                .addJavadoc(buildJavadoc(key, translations))
                                                .build());
    });

    // build and return the bundleEnum typeSpec
    return bundleEnumBuilder.build();
  }


  /**
   * Creates a javadoc code block for a single enum constant.
   * It contains the values for each language as an example.
   *
   * @param key the key of the resource
   * @param translations the translations of the key
   * @return
   */
  public CodeBlock buildJavadoc(final String key, final Collection<Translation> translations) {
    final CodeBlock.Builder javadocBuilder= CodeBlock.builder()
            .add("Returns the localized value of <code>$L</code>.\n", key)
            .add("<p>\n")
            .add("Examples:\n")
            .add("<ul>\n");
    for (final Translation translation : translations) {
      javadocBuilder.add("  <li>$L = $L</li>\n", translation.getLang(), translation.getValue());
    }
    javadocBuilder.add("</ul>\n");

    return javadocBuilder.build();
  }


  /**
   * Returns the valid class name for a given resource bundle.
   *
   * @param resourceContent the resource bundle for which to return the class name.
   * @return the class name for this resource bundle
   */
  private String getEnumClassNameFor(final FacadeBundleContent resourceContent) {
    return BundleNormalizer.toClassName(resourceContent.getBundleBaseName());
  }


  /**
   * Converts the given string (usually the key of a resource) to a format
   * suitable as an enum constant.
   *
   * @param s the string to convert
   * @return the converted string
   */
  private String toEnumConstName(final String s) {
    return BundleNormalizer.toConstName(s);
  }


  /**
   * Converts the given string (usually a path to a resource) to a format
   * suitable as a package name.
   *
   * @param s the string to convert
   * @return the converted string
   */
  public String toPackageName(final String s) {
    return s.replaceAll("\\/", ".");
  }


  /**
   * Copies the facade accessor classes to the specified target source directory.
   * The accessor class will be renamed to the value given in <code>accessorClassName</code>.
   * Be sure to give a valid java class name here.
   * <p>
   * The <code>targetSourcePath</code> is the path in which the package structure given in
   * <code>packageName</code> will be placed in.
   * <p>
   * This methods creates all necessary directories prior to writing the files.
   *
   * @param accessorClassName the class name to use for the facade accessor class
   * @param packageName the package into which to place the copied classes
   * @param targetSourcePath the path to the sources directory for the copied classes
   *                          (base of the package structure)
   */
  public void copyFacadeAccessorTemplates(final String accessorClassName, final String packageName, final Path targetSourcePath) {
    Objects.requireNonWhitespace(accessorClassName);
    Objects.requireNonWhitespace(packageName);
    java.util.Objects.requireNonNull(targetSourcePath);

    // first create the necessary directory structure
    final String packageDirStructure= packageName.replaceAll("\\.", "/");
    final Path targetDir= targetSourcePath.resolve(packageDirStructure);
    try {
      Files.createDirectories(targetDir);
    } catch (IOException ex) {
      throw new RuntimeException("Error creating target directory "+targetDir.toAbsolutePath().toString(), ex);
    }

    // then copy the I18nBundleKey class
    try(
      final InputStream in= this.getClass().getResourceAsStream("/facade/I18nBundleKey.java.template");
      final BufferedReader reader= new BufferedReader(new InputStreamReader(in));
      final PrintWriter writer= new PrintWriter(targetDir.resolve("I18nBundleKey.java").toFile());
      ) {
      String line;
      while ((line= reader.readLine()) != null) {
        // replace the package name
        if (line.trim().equals("package org.omnaest.i18nbinder.internal.facade;")) {
          line= line.replace("org.omnaest.i18nbinder.internal.facade", packageName);
        }

        writer.println(line);
      }
    } catch (IOException ex) {
      throw new RuntimeException("Error copying I18nBundleKey template to "+targetDir.toAbsolutePath().toString(), ex);
    }

    // then copy the I18n accessor class
    try(
      final InputStream in= this.getClass().getResourceAsStream("/facade/I18n.java.template");
      final BufferedReader reader= new BufferedReader(new InputStreamReader(in));
      final PrintWriter writer= new PrintWriter(targetDir.resolve(accessorClassName+".java").toFile());
      ) {
      String line;
      while ((line= reader.readLine()) != null) {
        // replace the package name
        if (line.trim().equals("package org.omnaest.i18nbinder.internal.facade;")) {
          line= line.replace("org.omnaest.i18nbinder.internal.facade", packageName);
        }

        // replace the class name
        line= line.replace("${ACCESSOR_CLASS_NAME}", accessorClassName);

        //TODO: Hier könnte man noch die Enum Facade Bundles referenzieren

        writer.println(line);
      }
    } catch (IOException ex) {
      throw new RuntimeException("Error copying facade accessor template to "+targetDir.toAbsolutePath().toString(), ex);
    }
  }
}
