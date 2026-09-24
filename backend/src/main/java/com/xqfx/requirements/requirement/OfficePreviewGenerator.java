package com.xqfx.requirements.requirement;

import java.io.IOException;
import java.nio.file.Path;

interface OfficePreviewGenerator {
    void convert(Path source, Path target) throws IOException;
}
