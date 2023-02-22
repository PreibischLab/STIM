package anndata;

public class AnnDataEncoding {
    protected final String encodingType;
    protected final String encodingVersion;

    public AnnDataEncoding(String encodingType, String encodingVersion) {
        this.encodingType = encodingType;
        this.encodingVersion = encodingVersion;
    }

    public String getType() {
        return encodingType;
    }

    public String getVersion() {
        return encodingVersion;
    }
}
