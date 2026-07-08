package com.imgood.textech.assistant;

import java.io.File;

import com.imgood.textech.TeXTechDataDir;

/**
 * Resolves TeXTech runtime data files under {@code TeXTech/<feature>/}.
 * Forge {@code .cfg} files are not handled here.
 */
public final class AssistantDataFiles {

    private AssistantDataFiles() {}

    public static File dataFile(String name) {
        if (name == null || name.isEmpty()) {
            return TeXTechDataDir.assistantRoot();
        }
        if (name.startsWith("web-")) {
            return TeXTechDataDir.webAeFile(name);
        }
        if (name.startsWith("grapple-")) {
            return TeXTechDataDir.grappleFile(name);
        }
        return TeXTechDataDir.assistantFile(name);
    }
}
