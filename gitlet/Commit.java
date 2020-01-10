package gitlet;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** commit class.
 * @author Yuan Sun
 * */
public class Commit implements Serializable, Iterable<Commit> {
    /** sha1 code for a commit. */
    private String _UID;
    /** parent for a commit. */
    private String _parent;
    /** second parent for a merge commit. */
    private String _secondParent;
    /** commit time. */
    private String _timeStamp;
    /** commit msg. */
    private String _message;
    /** storing all blobs. */
    private HashMap<String, String> _allBlobs;

    /** constructor for a single Commit.
     * given PARENT, SECONDPARENT, MESSAGE,
     * ADDFILES, DELFILES.
     */
    @SuppressWarnings("unchecked")
    public Commit(String parent, String secondParent, String message,
                  HashMap<String, String> addFiles,
                  HashMap<String, String> delFiles) {
        _parent = parent;
        _secondParent = secondParent;
        _message = message;
        _timeStamp = new SimpleDateFormat(
                "E MMM dd HH:mm:ss yyyy Z").format(new Date());
        if (!parent.equals("")) {
            Commit cur = (Commit) Main.read(Main.getCommitPath(), getParent());
            _allBlobs = (HashMap<String, String>) cur.getAllBlobs().clone();
        } else {
            _allBlobs = new HashMap<>();
        }

        for (Map.Entry<String, String> kv: addFiles.entrySet()) {
            _allBlobs.put(kv.getKey(), kv.getValue());
        }

        for (String fileName: delFiles.keySet()) {
            try {
                _allBlobs.remove(fileName);
            } catch (GitletException e) {
                System.out.println("this file hasn't been added");
                System.exit(0);
            }
        }
        String text = _timeStamp + message;
        text += _allBlobs;
        text += parent;
        text += secondParent;
        _UID = Utils.sha1(text);
    }

    /** return _allblobs. */
    public HashMap<String, String> getAllBlobs() {
        return _allBlobs;
    }

    /** return UID. */
    public String getUID() {
        return _UID;
    }

    /** return time. */
    public String getTime() {
        return _timeStamp;
    }

    /** return parent. */
    public String getParent() {
        return _parent;
    }

    /** set Parent to ID. */
    public void setParent(String id) {
        _parent = id;
    }

    /** return 2nd parent. */
    public String getSecondParent() {
        return _secondParent;
    }

    /** return msg. */
    public String getMsg() {
        return _message;
    }

    /** return iterator. */
    public Iterator<Commit> iterator() {
        return new CommitsIterator();
    }

    /** return _allblobs. */
    private class CommitsIterator implements Iterator<Commit> {
        /** current commit. */
        private Commit cur = (Commit) Main.read(Main.getCommitPath(),
                getUID());
        @Override
        public boolean hasNext() {
            return !cur.getParent().equals("");
        }

        @Override
        public Commit next() {
            cur = (Commit) Main.read(Main.getCommitPath(),
                    cur.getParent());
            return cur;
        }
    }
}
