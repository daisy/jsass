package io.bit3.jsass.adapter;

import io.bit3.jsass.context.ImportStack;
import io.bit3.jsass.importer.Import;
import io.bit3.jsass.importer.Importer;
import io.bit3.jsass.importer.JsassCustomHeaderImporter;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

class NativeImporterWrapper {
  private final ImportStack importStack;
  private final Importer importer;

  public NativeImporterWrapper(ImportStack importStack, Importer importer) {
    this.importStack = importStack;
    this.importer = importer;
  }

  public Collection<NativeImport> apply(String url, NativeImport previousNative) {
    try {
      Import previous = new Import(
          // importPath and absolutePath are possibly file paths and not valid URIs
          pathToUri(previousNative.importPath),
          pathToUri(previousNative.absolutePath),
          previousNative.contents,
          previousNative.sourceMap
      );

      boolean isNotJsassCustomImporter = !(importer instanceof JsassCustomHeaderImporter);
      Collection<Import> imports = this.importer.apply(url, previous);

      if (null == imports) {
        return null;
      }

      Collection<NativeImport> nativeImports = new LinkedList<>();

      for (Import importObject : imports) {
        if (isNotJsassCustomImporter) {
          NativeImport preImport = createPreImport(importObject);
          nativeImports.add(preImport);
        }

        nativeImports.add(new NativeImport(importObject));

        if (isNotJsassCustomImporter) {
          NativeImport postImport = createPostImport(importObject);
          nativeImports.add(postImport);
        }
      }

      return nativeImports;
    } catch (Throwable throwable) {
      throwable.printStackTrace(System.err);
      NativeImport nativeImport = new NativeImport(throwable);
      return Collections.singletonList(nativeImport);
    }
  }

  private static URI pathToUri(String path) throws URISyntaxException {
    File file = new File(path);
    if (file.exists()) {
      return file.toURI();
    } else {
      return new URI(path);
    }
  }

  private NativeImport createPreImport(Import importSource) {
    int id = importStack.register(importSource);

    StringBuilder preSource = new StringBuilder();

    // $jsass-void: jsass_import_stack_push(<id>) !global;
    preSource.append(
        String.format(
            "$jsass-void: jsass_import_stack_push(%d) !global;\n",
            id
        )
    );

    try {
      return new NativeImport(
          new Import(
              new URI(importSource.getAbsoluteUri() + "/JSASS_PRE_IMPORT.scss"),
              new URI(importSource.getAbsoluteUri() + "/JSASS_PRE_IMPORT.scss"),
              preSource.toString()
          )
      );
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private NativeImport createPostImport(Import importSource) {
    StringBuilder postSource = new StringBuilder();

    // $jsass-void: jsass_import_stack_pop() !global;
    postSource.append("$jsass-void: jsass_import_stack_pop() !global;\n");

    try {
      return new NativeImport(
          new Import(
              new URI(importSource.getAbsoluteUri() + "/JSASS_POST_IMPORT.scss"),
              new URI(importSource.getAbsoluteUri() + "/JSASS_POST_IMPORT.scss"),
              postSource.toString()
          )
      );
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
