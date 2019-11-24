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
package de.poiu.kilt.internal;

import de.poiu.apron.MissingKeyAction;
import de.poiu.apron.ApronOptions;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import de.poiu.kilt.internal.xls.I18nBundleKey;
import de.poiu.kilt.internal.xls.XlsFile;
import de.poiu.apron.PropertyFile;
import de.poiu.fez.Require;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_8;



public class XlsImExporter {
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

  public static void importXls(final Path propertiesRootDirectory,
                                 final File xlsFile,
                                 final Charset propertyFileEncoding,
                                 final MissingKeyAction missingKeyAction) {
    Require.nonNull(propertiesRootDirectory);
    Require.nonNull(xlsFile);

    final ApronOptions apronOptions= ApronOptions.create()
      .with(propertyFileEncoding != null ? propertyFileEncoding : UTF_8)
      .with(missingKeyAction);

    // read XLS file
    final XlsFile xlsFileObject= new XlsFile(xlsFile);
    final Map<I18nBundleKey, Collection<Translation>> content= xlsFileObject.getContent();

    // stores the mapping of resource bundle basenames and languages to the corresponding property files
    final Map<String, Map<Language, RememberingPropertyFile>> bundleFileMapping= new LinkedHashMap<>();

    // FIXME: Sort by bundleBasename and language? In that case we only have to have 1 property file open at a time
    content.entrySet().forEach((entry) -> {
      final I18nBundleKey bundleKey= entry.getKey();
      final String bundleBasename= bundleKey.getBundleBaseName();
      final String propertyKey= bundleKey.getKey();
      final Collection<Translation> translations= entry.getValue();

      // for each bundle…
      for (final Translation translation : translations) {
        if (!bundleFileMapping.containsKey(bundleBasename)) {
          bundleFileMapping.put(bundleBasename, new LinkedHashMap<>());
        }

        if (!bundleFileMapping.get(bundleBasename).containsKey(translation.getLang())) {
          final File fileForBundle= getFileForBundle(propertiesRootDirectory.toFile(), bundleBasename, translation.getLang());
          //TODO: Und hier müsste geprüft werden, ob das File in den i18nIncludes enthalten ist oder nicht.
          final PropertyFile propertyFile= new PropertyFile();
          bundleFileMapping.get(bundleBasename).put(translation.getLang(), new RememberingPropertyFile(fileForBundle, propertyFile));
        }

        final RememberingPropertyFile rpf= bundleFileMapping.get(bundleBasename).get(translation.getLang());
        // only write empty values if the key already exists in in the PropertyFile
        if ((translation.getValue() != null && !translation.getValue().isEmpty())
          || rpf.propertyFile.containsKey(propertyKey)) {
          rpf.propertyFile.setValue(propertyKey, translation.getValue());
        }
      }
    });

    //now write the property files back to disk
    bundleFileMapping.values().forEach((Map<Language, RememberingPropertyFile> langPropMap) -> {
      langPropMap.values().forEach((RememberingPropertyFile rpf) -> {
        // only write files if they have some content (avoid creating unwanted empty files for unsupported locales)
        if (rpf.propertyFile.propertiesSize()> 0) {
          rpf.propertyFile.saveTo(rpf.actualFile, apronOptions);
        }
      });
    });
  }


  public static void exportXls(final Path propertiesRootDirectory,
                               final Set<File> resourceBundleFiles,
                               final Charset propertyFileEncoding,
                               final Path xlsFilePath) {
    final ResourceBundleContentHelper fbcHelper= new ResourceBundleContentHelper(propertiesRootDirectory);
    final Map<String, Map<Language, File>> bundleNameToFilesMap= fbcHelper.toBundleNameToFilesMap(resourceBundleFiles);

    final XlsFile xlsFile= new XlsFile(xlsFilePath.toFile());

    bundleNameToFilesMap.entrySet().forEach((entry) -> {
      final String bundleName= entry.getKey();
      final Map<Language, File> bundleTranslations= entry.getValue();

      final ResourceBundleContent resourceBundleContent= ResourceBundleContent.forName(bundleName)
        .fromFiles(bundleTranslations, propertyFileEncoding !=null ? propertyFileEncoding : UTF_8);

      resourceBundleContent.getContent().asMap().entrySet().forEach((e) -> {
        final String propertyKey= e.getKey();
        final Collection<Translation> translations= e.getValue();

        xlsFile.setValue(new I18nBundleKey(bundleName, propertyKey), translations);
      });
    });

    xlsFile.save();
  }


  private static File getFileForBundle(final File propertiesRootDirectory, final String bundleBasename, final Language language) {
    final StringBuilder sb= new StringBuilder();

    sb.append(bundleBasename);
    if (!language.getLang().isEmpty()) {
      sb.append("_").append(language.getLang());
    }
    sb.append(".properties");

    return new File(propertiesRootDirectory, sb.toString());
  }

}
