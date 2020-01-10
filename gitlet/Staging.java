package gitlet;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/** staging class.
 * @author Yuan Sun
 * */
public class Staging implements Serializable {
    /** HashMap<String, String>. */
    private HashMap<String, String> _trackedBlobs;
    /** HashMap<String, String>. */
    private HashMap<String, String> _untracked;
    /** HashMap<String, String>. */
    private HashMap<String, String> _modifyTracked;
    /** HashMap<String, String>. */
    private HashMap<String, String> _removeTracked;
    /** HashMap<String, String>. */
    private HashMap<String, String> _addStage;
    /** HashMap<String, String>. */
    private HashMap<String, String> _removeStage;

    /** constructor for staging. */
    public Staging() {
        _trackedBlobs = new HashMap<>();
        _untracked = new HashMap<>();
        _modifyTracked = new HashMap<>();
        _removeTracked = new HashMap<>();
        _addStage = new HashMap<>();
        _removeStage = new HashMap<>();
    }

    /** Usage: java gitlet.Main add [file name]    .
     * Description: Adds a copy of the file based on the FILENAME
     * as it currently exists to the staging area
     * (see the description of the COMMIT command).
     * For this reason, adding a file is also called staging the file.
     * The staging area should be somewhere in .gitlet.
     * If the current working version of the file
     * is identical to the version in the current commit,
     * do not stage it to be added, and remove it
     * from the staging area if it is already there
     * (as can happen when a file is changed, added,
     * and then changed back).
     * If the file had been marked to be removed
     * (see gitlet rm), delete that mark.
     * Runtime:
     * In the worst case, should run in linear time
     * relative to the size of the file being added.
     * Failure cases: If the file does not exist,
     * print the error message File does not exist.
     * and exit without changing anything.
     */
    public void add(String fileName, Commit commit) {
        HashMap<String, String> allBlobs = getTrackedBlobs();
        File file = Paths.get(Main.getWorkingDir(), fileName).toFile();
        boolean modified;
        String uid;
        String contents = Utils.readContentsAsString(file);


        if (!_removeStage.containsKey(fileName)) {

            boolean tracked = allBlobs.containsKey(fileName);
            if (tracked) {
                modified = modified(fileName, contents, true);
                if (!modified) {
                    return;
                }
                _modifyTracked.put(fileName, "");
            }
            Blob blob = new Blob(fileName, contents);
            Main.write(Main.getTempBlobPath(), blob.getUID(), blob);
            uid = blob.getUID();
        } else {
            Utils.writeContents(file, contents);
            uid = allBlobs.get(fileName);
        }
        updateStage(fileName, uid);
    }

    /** rm, given FILENAME and COMMIT. */
    public void rm(String fileName, Commit commit) {
        HashMap<String, String> allTrackedBlobs = getTrackedBlobs();
        boolean tracked = allTrackedBlobs.containsKey(fileName);
        boolean staged = _addStage.containsKey(fileName)
                                 || _removeStage.containsKey(fileName);
        File file = Paths.get(Main.getWorkingDir(), fileName).toFile();
        if (tracked) {
            String uid = allTrackedBlobs.get(fileName);
            _addStage.remove(fileName);
            _removeStage.put(fileName, allTrackedBlobs.get(fileName));
            _modifyTracked.remove(fileName);
            _removeTracked.remove(fileName);
            Utils.restrictedDelete(file);
            File f = Paths.get(Main.getTempBlobPath(), uid).toFile();
            f.delete();
        } else if (staged) {
            _addStage.remove(fileName);
            if (!file.exists()) {
                _removeStage.put(fileName, _addStage.get(fileName));
            } else {
                _untracked.put(fileName, _addStage.get(fileName));
            }
        } else {
            System.out.println("No reason to remove the file.");
        }
    }

    /** handle status. */
    public void status() {
        String stagedInfo = printStaged();
        String removedInfo = printRemoved();
        String modifiedInfo = printModified();
        String untrackedInfo = printUntracked();
        untrackedInfo = untrackedInfo.trim();
        String all =  stagedInfo + removedInfo
                              + modifiedInfo + untrackedInfo;
        System.out.println(all);
    }

    /** Return sorted string, given LIST. */
    public String sort(ArrayList<String> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = i + 1; j < list.size(); j++) {
                String file1 = list.get(i);
                String file2 = list.get(j);
                if (file1.compareTo(file2) > 0) {
                    String temp = file2;
                    list.set(j, file1);
                    list.set(i, temp);
                }
            }
        }
        String files = "";
        for (String fileName: list) {
            files += (fileName + "\n");
        }
        files += "\n";
        return files;
    }

    /** Return updated untracked FILENAMES.
     *  in case the untracked files are not in the
     *  working dir.
     */
    public Set<String> update(Set<String> fileNames) {
        Set<String> intersection = new HashSet<>(fileNames);
        Set<String> workingFiles = new HashSet<>(
                Utils.plainFilenamesIn(Main.getWorkingDir()));
        workingFiles.remove("commitsConfig.bin");
        workingFiles.remove("stagingConfig.bin");
        intersection.retainAll(workingFiles);
        return intersection;
    }

    /** return String. */
    public String printUntracked() {
        Set<String> fileNames =
                getUntracked().keySet();
        fileNames = update(fileNames);
        fileNames.addAll(Main.getNewFiles());
        String header = "=== Untracked Files ===" + "\n";
        ArrayList<String> unsorted = new ArrayList<>(fileNames);
        header += sort(unsorted);
        return header;
    }

    /** Return sorted strings, given LIST. */
    public ArrayList<String> sortModified(ArrayList<String> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = i + 1; j < list.size(); j++) {
                String file1 = list.get(i);
                String file2 = list.get(j);
                if (file1.compareTo(file2) > 0) {
                    String temp = file2;
                    list.set(j, file1);
                    list.set(i, temp);
                }
            }
        }
        return list;
    }

    /** Return print modified string. */
    public String printModified() {
        Set<String> modified =
                getModified().keySet();
        Set<String> deleted =
                getDeleted().keySet();

        String header = "=== Modifications Not Staged For Commit ===" + "\n";

        ArrayList<String> unsorted = new ArrayList<>(deleted);
        HashSet<String> justDeleted = Main.getJustDeleted();
        if (!justDeleted.isEmpty()) {
            unsorted.addAll(justDeleted);
        }

        ArrayList<String> files = sortModified(unsorted);

        for (String fileName: files) {
            header += (fileName + " (deleted)" + "\n");
        }

        unsorted = new ArrayList<>(modified);

        HashSet<String> toCheck = Main.getJustModified();
        if (!toCheck.isEmpty()) {
            unsorted.addAll(toCheck);
        }

        files = sortModified(unsorted);

        for (String fileName: files) {
            header += (fileName + " (modified)" + "\n");
        }
        header += "\n";
        return header;
    }

    /** return removed string. */
    public String printRemoved() {
        Set<String> fileNames =
                getRemoveStage().keySet();
        String header = "=== Removed Files ===" + "\n";
        ArrayList<String> unsorted = new ArrayList<>(fileNames);
        header += sort(unsorted);
        return header;
    }

    /** return staged string. */
    public String printStaged() {
        Set<String> fileNames =
                getAddStage().keySet();
        String header = "=== Staged Files ===" + "\n";
        ArrayList<String> unsorted = new ArrayList<>(fileNames);
        header += sort(unsorted);
        return header;
    }
    /** a FILENAME with UID could have 4 stages.
     * we need to update them accordingly after a staging occurs
     *  1. staged as removal, then remove this record from _removeStage
     *  2. tracked but was removed, then remove this record from _removeTracked
     *     and put it into removeStage for further operations.
     *  3. tracked but was modified, removed this record from _modifyTracked
     *      then put it into _addStage for further operations.
     *  4. not tracked at all, remove this from _untracked
     *     and put it into _addStage for further operations.
     */
    public void updateStage(String fileName, String uid) {
        boolean stagedAsRemoved = _removeStage.containsKey(fileName);
        boolean trackedDel = _removeTracked.containsKey(fileName);
        boolean trackedModified = _modifyTracked.containsKey(fileName);
        if (trackedDel) {
            _removeTracked.remove(fileName);
            _removeStage.put(fileName, uid);
        } else if (trackedModified) {
            _modifyTracked.remove(fileName);
            _addStage.put(fileName, uid);
        } else if (stagedAsRemoved) {
            _removeStage.remove(fileName);
        } else {
            _untracked.remove(fileName);
            _addStage.put(fileName, uid);
        }
    }

    /** post merge update.
     * given UPDATEBLOBS, RMBLOBS, CONFLICTS
     * @param updateBlobs updated blobs
     * @param rmBlobs removed blobs
     * @param conflicts conflicts
     */
    public void mergeUpdate(HashMap<String, String> updateBlobs,
                            HashMap<String, String> rmBlobs,
                            HashMap<String, String> conflicts) {
        for (String fileName: updateBlobs.keySet()) {
            add(fileName, Main.getAllCommits().getCurrentCommit());
        }

        for (String fileName: rmBlobs.keySet()) {
            _removeStage.put(fileName, rmBlobs.get(fileName));
        }

        for (String fileName: conflicts.keySet()) {
            _modifyTracked.put(fileName, null);
        }
    }

    /** Check whether the files in the staging area have been tracked or not.
     */
    public void stagedAllTracked() {
        List<String> fileNames = Utils.plainFilenamesIn
                                               (Main.getWorkingDir());

        for (String fileName: fileNames) {
            File f = Paths.get(Main.getWorkingDir(), fileName).toFile();
            String contents = Utils.readContentsAsString(f);

            if (getTrackedBlobs().containsKey(fileName)
                && modified(fileName, contents, true)) {
                _modifyTracked.put(fileName, "");
            } else if (((!getTrackedBlobs().containsKey(fileName))
                        && getAddStage().containsKey(fileName)
                        && modified(fileName, contents, false))
                        || (!getTrackedBlobs().containsKey(fileName)
                        && !getAddStage().containsKey(fileName))) {
                _untracked.put(fileName, "");
            }
        }

        for (String fileName: _untracked.keySet()) {
            File f = Paths.get(Main.getWorkingDir(), fileName).toFile();
            if (!f.exists()) {
                _untracked.remove(fileName);
            }
        }

        for (String fileName: getTrackedBlobs().keySet()) {
            File f = Paths.get(Main.getWorkingDir(), fileName).toFile();
            if (!f.exists()) {
                _removeStage.put(fileName, getTrackedBlobs().get(fileName));
            }
        }

    }
    /** Return _ADDSTAGE. */
    public HashMap<String, String> getAddStage() {
        return _addStage;
    }

    /** Return _REMOVESTAGE. */
    public HashMap<String, String> getRemoveStage() {
        return _removeStage;
    }

    /** Update my blobs to be the blobs of newest COMMIT. */
    public void setBlobs(Commit commit) {
        _trackedBlobs = commit.getAllBlobs();
    }

    /** Clear the staging area. */
    public void clearAll() {
        _modifyTracked.clear();
        _removeTracked.clear();
        _addStage.clear();
        _removeStage.clear();
    }

    /** return Tracked files. */
    public HashMap<String, String> getTrackedBlobs() {
        return _trackedBlobs;
    }

    /** return untracked files. */
    public HashMap<String, String> getUntracked() {
        return _untracked;
    }

    /** return removed blobs. */
    public HashMap<String, String> getModified() {
        return _modifyTracked;
    }

    /** return removed tracked blobs. */
    public HashMap<String, String> getDeleted() {
        return _removeTracked;
    }

    /** Return true if the FILENAME's CONTENT has been modified.
     *  otherwise False.
     * given TRACKED */
    private boolean modified(String fileName, String content, Boolean tracked) {
        String currContent = getContents(fileName, tracked);
        return !(currContent.equals(content));
    }

    /** if the FILENAME is not TRACKED by the commit.
     * return contents in .tempBlob.
     * else, return contents in .blob
     * @param fileName a file name,
     * @param tracked boolean value.
     */
    private String getContents(String fileName, Boolean tracked) {
        Blob blob;
        String contents;
        if (tracked) {
            blob = (Blob) Main.read(Main.getBlobPath(),
                    getTrackedBlobs().get(fileName));
            contents = blob.getContents();
        } else {
            blob = (Blob) Main.read(Main.getTempBlobPath(),
                    getAddStage().get(fileName));
            contents = blob.getContents();
        }
        return contents;
    }
}
