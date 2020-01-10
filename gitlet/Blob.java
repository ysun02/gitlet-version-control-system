package gitlet;

import java.io.Serializable;
/** Blob class.
 * @author Yuan Sun
 */
public class Blob implements Serializable {
    /** fileName. */
    private String _fileName;
    /** UID for a blob. */
    private String _UID;
    /** contents of file. */
    private String _contents;

    /** constructor of the blob.
     * Given FILENAME, CONTENTS */
    Blob(String fileName, String contents) {
        _fileName = fileName;
        _contents = contents;
        String text = fileName + contents;
        _UID = Utils.sha1(text);
    }

    /** return UID of the blob. */
    public String getUID() {
        return _UID;
    }

    /** return FILENAME of the blob. */
    public String getFileName() {
        return _fileName;
    }

    /** return CONTENTS of the blob. */
    public String getContents() {
        return _contents;
    }

    /** set Contents to CONTENTS. */
    public void setContents(String contents) {
        _contents = contents;
    }
}

