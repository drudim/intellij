package com.google.idea.blaze.python.projectstructure;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.PyIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.sync.SourceFolderProvider;
import com.google.idea.blaze.base.util.UrlUtil;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class PythonSourceFolderProvider implements SourceFolderProvider {
    private ImmutableList<String> sourceRoots;

    public PythonSourceFolderProvider(TargetMap targetMap) {
        ImmutableList<PyIdeInfo> pythonTargets = targetMap
                .targets()
                .stream()
                .filter(t -> t.getPyIdeInfo() != null)
                .map(TargetIdeInfo::getPyIdeInfo).collect(toImmutableList());

        sourceRoots = pythonTargets
                .stream()
                .map(PyIdeInfo::getSources)
                .map(s -> s
                        .stream()
                        .filter(ArtifactLocation::isMainWorkspaceSourceArtifact)
                        .filter(a -> a
                                .getRelativePath()
                                .endsWith("/__init__.py"))
                        .min((a, b) ->
                                Integer.compare(
                                        CharMatcher.is('/').countIn(a.getRelativePath()),
                                        CharMatcher.is('/').countIn(b.getRelativePath())
                                )
                        ))
                .filter(Optional::isPresent)
                .map(a -> StringUtils.removeEnd(a.get().getRelativePath(), "__init__.py"))
                .collect(toImmutableList());
    }

    @Override
    public ImmutableMap<File, SourceFolder> initializeSourceFolders(ContentEntry contentEntry) {
        String url = contentEntry.getUrl();
        File contentFile = UrlUtil.urlToFile(url);
        String contentFileName = contentFile.getName();

        Map<File, SourceFolder> sourceFolders = sourceRoots
                .stream()
                .filter(s -> s.startsWith(contentFileName))
                .map(s -> new File(contentFile, s.substring(contentFileName.length())))
                .collect(Collectors.toMap(
                        f -> f,
                        f -> contentEntry.addSourceFolder("file://" + f.getAbsolutePath(), false)
                ));

        return ImmutableMap.copyOf(sourceFolders);
    }

    @Override
    public SourceFolder setSourceFolderForLocation(ContentEntry contentEntry, SourceFolder parentFolder, File file, boolean isTestSource) {
        return contentEntry.addSourceFolder(UrlUtil.fileToIdeaUrl(file), isTestSource);
    }
}
