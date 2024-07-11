package io;

import org.janelia.n5anndata.io.AnnDataFieldType;
import org.janelia.saalfeldlab.n5.N5Writer;


class AnnDataDetails {

    public static void writeEncoding(N5Writer writer, String path, AnnDataFieldType type) {
        writer.setAttribute(path, "encoding-type", type.getEncoding());
        writer.setAttribute(path, "encoding-version", type.getVersion());
    }

    protected static void createMapping(N5Writer writer, String path) {
        writer.createGroup(path);
        writeEncoding(writer, path, AnnDataFieldType.MAPPING);
    }

}
