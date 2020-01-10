package gitlet;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** commitstree class.
 * @author Yuan Sun
 */
public class CommitsTree implements Serializable {
    /** storing all branches. */
    private HashMap<String, String> _allBranches;
    /** current branch name. */
    private String _currentBranch;

    /** constructor for commitstree. */
    public CommitsTree() {
        _allBranches = new HashMap<>();
        Commit initCommit = new Commit("", "",
                "initial commit", new HashMap<>(), new HashMap<>());
        String initID = initCommit.getUID();
        Main.write(Main.getCommitPath(), initID, initCommit);
        _allBranches.put("master", initID);
        _currentBranch = "master";
    }

    /** return true if remote branch's ID is in the history.
     * of the current local head.
     */
    public Boolean inHistory(String id) {
        Commit currentHead = getCurrentCommit();
        Iterator<Commit> iter = currentHead.iterator();
        while (iter.hasNext()) {
            Commit next = iter.next();
            if (next.getUID().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** command for push [REMOTENAME] [BRANCHNAME]
     *  given REMOTEPATH.
     */
    public void push(String remotePath,
                            String remoteName, String branchName) {
        File treeConfig = Paths.get(
                remotePath, "commitsConfig.bin").toFile();
        CommitsTree remoteTrees = null;
        if (treeConfig.exists()) {
            remoteTrees = Utils.readObject(
                    treeConfig, CommitsTree.class);
        }
        File stageConfig = Paths.get(
                remotePath, "stagingConfig.bin").toFile();
        Staging remoteStage = Utils.readObject(stageConfig, Staging.class);
        HashMap<String, String> remoteBranches = remoteTrees.getAllBranches();
        String remoteHeadID = remoteBranches.get(branchName);
        if (remoteHeadID.equals(
                getCurrentCommit().getUID())) {
            return;
        }
        if (!inHistory(remoteHeadID)) {
            System.out.println(
                    "Please pull down remote changes before pushing.");
            System.exit(0);
            return;
        }
        remoteTrees.reset(getCurrentCommit().getUID());
        Main.write(Main.getRemoteCommitsPath(remotePath),
                getCurrentCommit().getUID(), getCurrentCommit());
        Iterator<Commit> currIter = getCurrentCommit().iterator();

        while (currIter.hasNext()) {
            Commit next = currIter.next();
            if (next.getUID().equals(remoteHeadID)) {
                break;
            }
            Main.write(Main.getRemoteCommitsPath(remotePath),
                    next.getUID(), next);
        }
        remoteTrees.getAllBranches().put(
                branchName, getCurrentCommit().getUID());
        remoteTrees.reset(remoteTrees.getCurrentCommit().getUID());
        Main.write(remotePath, "commitsConfig.bin", remoteTrees);
        Main.write(remotePath, "stagingConfig.bin", remoteStage);
    }

    /** fetch REMOTEPATH, REMOTENAME BRANCHNAME. */
    public void fetch(String remotePath,
                      String remoteName, String branchName) {
        CommitsTree remoteTrees = Main.getRemoteTree(remoteName);
        if (!remoteTrees.getAllBranches().containsKey(
                branchName)) {
            System.out.println("That remote does not have that branch.");
            System.exit(0);
            return;
        }
        String remoteCommitsPath = Main.getRemoteCommitsPath(remotePath);
        String headRemoteID = remoteTrees.getAllBranches().get(branchName);
        Commit headRemoteCommit = (Commit) Main.read(remoteCommitsPath,
                headRemoteID);
        writeToLocalCommits(headRemoteCommit, headRemoteID);

        HashMap<String, String> allBlobs = headRemoteCommit.getAllBlobs();
        writeToLocalBlobs(allBlobs, remotePath);

        while (!headRemoteCommit.getParent().equals("")) {
            Commit next = (Commit) Main.read(
                    remoteCommitsPath, headRemoteCommit.getParent());
            writeToLocalCommits(next, next.getUID());
            headRemoteCommit = next;
        }
        writeToLocalCommits(headRemoteCommit,
                headRemoteCommit.getUID());
        _allBranches.put(remoteName + "/" + branchName,
                headRemoteID);
    }
    /** pull REMOTEPATH, REMOTENAME REMOTEBRANCH. */
    public void pull(String remotePath,
                     String remoteName, String remoteBranch) {
        fetch(remotePath, remoteName, remoteBranch);
        merge(remoteName + "/" + remoteBranch);
    }
    /** write to commits given COMMIT, COMMITID. */
    public void writeToLocalCommits(Commit commit, String commitID) {
        List<String> currentCommits = Utils.plainFilenamesIn(
                Main.getCommitPath());
        if (!currentCommits.contains(commitID)) {
            Main.write(Main.getCommitPath(), commitID, commit);
        }
    }
    /** write to blobs given ALLBLOBS, REMOTEPATH. */
    public void writeToLocalBlobs(HashMap<String, String> allBlobs,
                                  String remotePath) {
        List<String> currentBlobs = Utils.plainFilenamesIn(
                Main.getBlobPath());
        for (Map.Entry<String, String> kv: allBlobs.entrySet()) {
            if (!currentBlobs.contains(kv.getValue())) {
                Blob blob = (Blob) Main.read(
                        Main.getRemoteBlobs(remotePath), kv.getValue());
                Main.write(Main.getBlobPath(), kv.getValue(), blob);
            }
        }
    }
    /** append new commits to exisiting remote ID
     * with BRANCHNAME, REMOTETREE and REMOTESTAGING. */
    public void append(String branchName, CommitsTree remoteTree,
                              Staging remoteStaging) {
        Commit currentHead = getCurrentCommit();
        getAllBranches().put(branchName, currentHead.getUID());
        remoteTree.reset(currentHead.getUID());
    }

    /** log. */
    public void log() {
        Commit c = getCurrentCommit();
        Iterator<Commit> iterator = getCurrentCommit().iterator();
        singleLog(getCurrentCommit());
        while (iterator.hasNext()) {
            Commit next = iterator.next();
            if (!next.getSecondParent().equals("")) {
                mergeLog(next);
            } else {
                singleLog(next);
            }

        }
    }

    /** setCurrentCommit to be C. */
    public void setCurrentCommit(Commit c) {
        _allBranches.put(_currentBranch, c.getUID());
    }

    /** return full commit ID if given an abbreviated ID.
     * if ID does not exist in the commit path, return an empty string.
     */
    public String validateID(String id) {
        ArrayList<Object> files = Main.readAll(Main.getCommitPath());
        for (Object file: files) {
            Commit commit = (Commit) file;
            String fullID = commit.getUID();
            if (fullID.substring(0, id.length()).equals(id)) {
                return fullID;
            }
        }
        return "";
    }

    /** print log for a merge COMMIT.
     *    ===
     *    commit 3e8bf1d794ca2e9ef8a4007275acf3751c7170ff
     *    Merge: 4975af1 2c1ead1
     *    Date: Sat Nov 11 12:30:00 2017 -0800
     *    Merged development into master.
     */
    public void mergeLog(Commit commit) {
        String separator = "===\n";
        String uid = "commit " + commit.getUID() + "\n";
        String merge = "Merge: " + commit.getParent() + " "
                               + commit.getSecondParent() + "\n";
        String date = "Date: " + commit.getTime() + "\n";
        String msg = commit.getMsg() + "\n";
        String complete = separator + uid + merge + date + msg;
        System.out.println(complete);
    }

    /** helper function for global log.
     * given a head COMMIT.
     */
    public void logCommit(Commit commit) {
        Iterator<Commit> iterator = commit.iterator();
        singleLog(commit);
        while (iterator.hasNext()) {
            Commit next = iterator.next();
            singleLog(next);
        }
    }

    /** global-log. */
    public void globalLog() {
        ArrayList<Object> allCommits = Main.readAll(Main.getCommitPath());
        Set<Commit> toLog = new HashSet<>();
        for (Object obj: allCommits) {
            Commit commit = (Commit) obj;
            toLog.add(commit);
        }
        for (Commit commit: toLog) {
            logCommit(commit);
        }
    }

    /** find MSG. */
    public void findMsg(String msg) {
        boolean flag = false;
        List<String> fileNames = Utils.plainFilenamesIn(Main.getCommitPath());
        for (String code: fileNames) {
            Commit commit = (Commit) Main.read(Main.getCommitPath(), code);
            String message = commit.getMsg();
            if (message.equals(msg)) {
                flag = true;
                System.out.println(commit.getUID());
            }
        }
        if (!flag) {
            System.out.println("Found no commit with that message.");
        }
    }

    /** Prints out status of gitlet.
     * === Branches ===
     * *master
     * other-branch
     *
     * === Staged Files ===
     * wug.txt
     * wug2.txt
     *
     * === Removed Files ===
     * goodbye.txt
     *
     * === Modifications Not Staged For Commit ===
     * junk.txt (deleted)
     * wug3.txt (modified)
     *
     * === Untracked Files ===
     * random.stuff
     */
    public void status() {
        String branchInfo = printBranches();
        System.out.println(branchInfo);
    }

    /** Return sorted LIST. */
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
        return files;
    }

    /** Return branches string. */
    public String printBranches() {
        String header = "=== Branches ===" + "\n";
        header += "*" + _currentBranch + "\n";
        Set<String> branches = new HashSet<>(getAllBranches().keySet());
        branches.remove(_currentBranch);
        ArrayList<String> unsorted = new ArrayList<>(branches);
        header += sort(unsorted);
        return header;
    }

    /** log COMMIT. */
    public void singleLog(Commit commit) {
        String separator = "===\n";
        String uid = "commit " + commit.getUID() + "\n";
        String date = "Date: " + commit.getTime() + "\n";
        String msg = commit.getMsg() + "\n";
        String complete = separator + uid + date + msg;
        System.out.println(complete);
    }

    /** branch NAME. */
    public void branch(String name) {
        if (_allBranches.containsKey(name)) {
            System.out.println("A branch with that name already exists.");
        } else {
            String sha1 = _allBranches.get(_currentBranch);
            _allBranches.put(name, sha1);
        }
    }

    /** add to commit tree, given MSG, SECONDPARENT, ADD, DEL. */
    public void addCommit(String msg, String secondParent,
                          HashMap<String, String> add,
        HashMap<String, String> del) {
        String parentUID = getCurrentCommit().getUID();
        Commit commit = new Commit(parentUID, secondParent, msg,
                add, del);
        Main.write(Main.getCommitPath(), commit.getUID(), commit);
        ArrayList<Object> tempFiles = Main.readAll(Main.getTempBlobPath());
        for (Object file: tempFiles) {
            Blob temp = (Blob) file;
            File f = Utils.join(Main.getTempBlobPath(), temp.getUID());
            Main.write(Main.getBlobPath(), temp.getUID(), temp);
            if (!f.delete()) {
                System.out.println("deletion failed.");
                System.exit(0);
            }
        }
        _allBranches.put(_currentBranch, commit.getUID());
        commit = getCurrentCommit();
        Main.getStaging().setBlobs(commit);
        Main.getStaging().clearAll();

    }

    /** checkout -- FILENAME. */
    public void checkoutFile(String fileName) {
        HashMap<String, String> allBlobs = getCurrentCommit().getAllBlobs();
        if (allBlobs.containsKey(fileName)) {
            Blob update = (Blob) Main.read(Main.getBlobPath(),
                    allBlobs.get(fileName));
            String contents = update.getContents();
            File workFile = Utils.join(Main.getWorkingDir(), fileName);
            Utils.writeContents(workFile, contents);
        } else {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }

    }

    /** rm branch, given NAME. */
    public void rmBranch(String name) {
        if (!_allBranches.containsKey(name)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        } else if (_currentBranch.equals(name)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        } else {
            _allBranches.remove(name);
        }
    }

    /** return current commit of the branch (head commit). */
    public Commit getCurrentCommit() {
        String commitSHA1 = _allBranches.get(_currentBranch);
        String commitPath = Main.getCommitPath();
        Commit commit = (Commit) Main.read(commitPath, commitSHA1);
        return commit;
    }

    /** Takes the version of the FILENAME.
     *  as it exists in the commit with COMMITID.
     * and puts it in the working directory,
     * overwriting the version of the file that's already there if there is one.
     * The new version of the file is not staged.
      */
    public void checkoutCommit(String commitID, String fileName) {
        commitID = validateID(commitID);
        if (commitID.equals("")) {
            System.out.println("No commit with that id exists. ");
            return;
        }
        Commit commit = (Commit) Main.read(Main.getCommitPath(), commitID);
        HashMap<String, String> blobs = commit.getAllBlobs();
        if (!blobs.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
        } else {
            Blob blob = (Blob) Main.read(Main.getBlobPath(),
                    blobs.get(fileName));
            File workingFile = Paths.get(Main.getWorkingDir(),
                    fileName).toFile();
            Utils.writeContents(workingFile, blob.getContents());
        }
    }

    /** reset, given COMMITID. */
    public void reset(String commitID) {
        commitID = validateID(commitID);
        if (commitID.equals("")) {
            System.out.println("No commit with that id exists. ");
            return;
        }
        Commit commit = (Commit) Main.read(Main.getCommitPath(), commitID);
        HashMap<String, String> trackedFiles = commit.getAllBlobs();
        removeIfNotTracked(commitID);
        for (String fileName: trackedFiles.keySet()) {
            checkoutCommit(commitID, fileName);
        }
        _allBranches.put(_currentBranch, commitID);
        Main.getStaging().setBlobs(commit);
        Main.getStaging().clearAll();
        Main.getStaging().stagedAllTracked();
    }

    /** Return the split point of commit CURRENT and OTHER.
     *  remember to add the head commit of other as well.
     */
    public Commit splitPoint(Commit current, Commit other) {
        Iterator<Commit> currentIter = getCurrentCommit().iterator();
        Iterator<Commit> otherIter = other.iterator();
        Iterator<Commit> secondIter = other.iterator();
        Set<String> otherContainer = new HashSet<>();
        Stack<Commit> list = new Stack<>();
        Set<String> visited = new HashSet<>();
        list.add(other);
        while (!list.isEmpty()) {
            Commit top = list.pop();
            if (!visited.contains(top.getUID())) {
                otherContainer.add(top.getUID());
                visited.add(top.getUID());
                if (!top.getParent().equals("")) {
                    Commit parent = (Commit) Main.read(
                            Main.getCommitPath(), top.getParent());
                    list.add(parent);
                }
                if (!top.getSecondParent().equals("")) {
                    Commit parent = (Commit) Main.read(
                            Main.getCommitPath(), top.getSecondParent());
                    list.add(parent);
                }
            }
        }

        Set<String> currContainer = new HashSet<>();
        Stack<Commit> currList = new Stack<>();
        Set<String> currVisited = new HashSet<>();
        currList.add(current);
        while (!currList.isEmpty()) {
            Commit top = currList.pop();
            if (otherContainer.contains(top.getUID())) {
                return top;
            }
            if (!currVisited.contains(top.getUID())) {
                currContainer.add(top.getUID());
                currVisited.add(top.getUID());
                if (!top.getParent().equals("")) {
                    Commit parent = (Commit) Main.read(
                            Main.getCommitPath(), top.getParent());
                    if (otherContainer.contains(parent.getUID())) {
                        return parent;
                    }
                    currList.add(parent);
                }
                if (!top.getSecondParent().equals("")) {
                    Commit parent = (Commit) Main.read(
                            Main.getCommitPath(), top.getSecondParent());
                    if (otherContainer.contains(parent.getUID())) {
                        return parent;
                    }
                    currList.add(parent);
                }
            }
        }
        return null;
    }

    /** merge helper for exception case.
     * Given OTHERCOMMIT, OTHERBLOBS, SPLITCOMMITBLOBS, CURRBLOBS, RMBLOBS,
     * UPDATEBLOBS, CONFLICTS.
     */
    public void mergeExcept(Commit otherCommit,
                            HashMap<String, String> otherBlobs,
                            HashMap<String, String> splitCommitBlobs,
                            HashMap<String, String> currBlobs,
                            HashMap<String, String> rmBlobs,
                            HashMap<String, String> updateBlobs,
                            HashMap<String, String> conflicts) {
        Main.write(Main.getGitPath(), ".tempStaging", Main.getStaging());
        Main.getStaging().stagedAllTracked();
        handleMerge(otherCommit, otherBlobs,
                splitCommitBlobs, currBlobs,
                rmBlobs, updateBlobs, conflicts);
        Staging stage = Main.getStaging();
        Set<String> currentUntracked = stage.getUntracked().keySet();
        Set<String> copy = new HashSet<>(currentUntracked);
        Set<String> update = updateBlobs.keySet();
        Set<String> rm = rmBlobs.keySet();
        currentUntracked.retainAll(update);
        copy.retainAll(rm);
        boolean updateEmpty = currentUntracked.isEmpty();
        boolean rmEmpty = copy.isEmpty();
        if (!updateEmpty || !rmEmpty) {
            System.out.println("There is an untracked file in the way; "
                                       + "delete it or add it first.");
            Staging stage1 = (Staging) Main.read(Main.getGitPath(),
                    ".tempStaging");
            Main.setStaging(stage1);
            File f = Utils.join(Main.getGitPath(), ".tempStaging");
            String file = f.toString();
            f.delete();
            System.exit(0);
        }
    }
    /** handle edge cases for merge BRANCH. */
    public void mergeWrong(String branch) {
        if (!_allBranches.containsKey(branch)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
        if (_currentBranch.equals(branch)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
    }

    /** merge, given BRANCH. */
    public void merge(String branch) {
        boolean noAdded = Main.getStaging().getAddStage().isEmpty();
        boolean noRemoved = Main.getStaging().getRemoveStage().isEmpty();
        if (!noAdded || !noRemoved) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }
        mergeWrong(branch);
        Commit otherCommit = (Commit) Main.read(Main.getCommitPath(),
                _allBranches.get(branch));
        Commit current = getCurrentCommit();
        Commit splitCommit = splitPoint(current, otherCommit);
        if (splitCommit.getUID().equals(otherCommit.getUID())) {
            System.out.println("Given branch is an "
                                       + "ancestor of the current branch.");
            return;
        }
        if (splitCommit.getUID().equals(getCurrentCommit().getUID())) {
            reset(otherCommit.getUID());
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        HashMap<String, String> otherBlobs = otherCommit.getAllBlobs();
        HashMap<String, String> splitCommitBlobs = splitCommit.getAllBlobs();
        HashMap<String, String> currBlobs = getCurrentCommit().getAllBlobs();
        HashMap<String, String> rmBlobs = new HashMap<>();
        HashMap<String, String> updateBlobs = new HashMap<>();
        HashMap<String, String> conflicts = new HashMap<>();
        mergeExcept(otherCommit, otherBlobs, splitCommitBlobs, currBlobs,
                rmBlobs, updateBlobs, conflicts);
        for (String fileName: currBlobs.keySet()) {
            if (splitCommitBlobs.containsKey(fileName)
                && !otherBlobs.containsKey(fileName)) {
                if (currBlobs.get(fileName).equals(
                        splitCommitBlobs.get(fileName))) {
                    File f = Utils.join(Main.getWorkingDir(), fileName);
                    Utils.restrictedDelete(f);
                }
            }
        }
        String msg = "Merged " + branch + " into " + _currentBranch + ".";
        if (conflicts.isEmpty()) {
            addCommit(msg, otherCommit.getUID(), updateBlobs, rmBlobs);
        } else {
            List<String> fileNames = Utils.plainFilenamesIn
                                                   (Main.getWorkingDir());
            for (String fileName: fileNames) {
                if (conflicts.containsKey(fileName)) {
                    File f = Utils.join(Main.getWorkingDir(), fileName);
                    Utils.writeContents(f, conflicts.get(fileName));
                    Main.getStaging().add(fileName, getCurrentCommit());
                }
            }
            Main.getStaging().mergeUpdate(updateBlobs, rmBlobs, conflicts);
            addCommit(msg, otherCommit.getUID(), updateBlobs, rmBlobs);
            fixStaging(conflicts);
            System.out.println("Encountered a merge conflict.");
        }
        Main.getStaging().clearAll();
    }

    /** fix staging area with CONFLICTS. */
    public void fixStaging(HashMap<String, String> conflicts) {
        HashMap<String, String> blobs = getCurrentCommit().getAllBlobs();
        for (String fileName: conflicts.keySet()) {
            if (blobs.containsKey(fileName)) {
                Blob blob = (Blob) Main.read(
                        Main.getBlobPath(), blobs.get(fileName));
                blob.setContents(conflicts.get(fileName));
            }
        }
    }
    /** given OTHER, OTHERBLOBS.
     * SPLITCOMMITBLOBS, CURRBLOBS, UPDATEBLOBS, CONFLICTS.
     * help merge. */
    public void mergePart1(Commit other,
                           HashMap<String, String> otherBlobs,
                           HashMap<String, String> splitCommitBlobs,
                           HashMap<String, String> currBlobs,
                           HashMap<String, String> updateBlobs,
                           HashMap<String, String> conflicts) {
        for (String fileName: otherBlobs.keySet()) {
            boolean currContain = currBlobs.containsKey(fileName);
            boolean splitContain = splitCommitBlobs.containsKey(fileName);
            if (currContain && splitContain) {
                String splitSha1 = splitCommitBlobs.get(fileName);
                boolean currUnchanged = currBlobs.get(fileName).equals(
                        splitSha1);
                boolean otherUnchanged = otherBlobs.get(fileName).equals(
                        splitSha1);
                String contents = currBlobs.get(fileName);
                boolean sameWay = contents.equals(otherBlobs.get(fileName));
                if (currUnchanged && !otherUnchanged
                            || otherUnchanged && !currUnchanged) {
                    updateBlobs.put(fileName, splitSha1);
                } else if (!currUnchanged && !otherUnchanged && !sameWay) {
                    updateConflict(currBlobs, otherBlobs, conflicts, fileName);
                }
            }
            if (!splitContain && !currContain) {
                checkoutCommit(other.getUID(), fileName);
                updateBlobs.put(fileName, otherBlobs.get(fileName));
            }
            if (currContain && !splitContain) {
                String contents = currBlobs.get(fileName);
                boolean sameWay = contents.equals(otherBlobs.get(fileName));
                if (!sameWay) {
                    updateConflict(currBlobs, otherBlobs, conflicts, fileName);
                }
            }
            if (splitContain && !currContain) {
                String splitSha1 = splitCommitBlobs.get(fileName);
                boolean otherUnchanged = otherBlobs.get(fileName).equals(
                        splitSha1);
                if (!otherUnchanged) {
                    updateConflict(currBlobs, otherBlobs, conflicts, fileName);
                }
            }
        }
    }

    /** helper for handleMerge.
     * given OTHER, OTHERBLOBS, SPLITCOMMITBLOBS,
     * CURRBLOBS, RMBLOBS, UPDATEBLOBS, CONFLICTS.
     */
    public void mergePart2(HashMap<String, String> otherBlobs,
                           HashMap<String, String> splitCommitBlobs,
                           HashMap<String, String> currBlobs,
                           HashMap<String, String> rmBlobs,
                           HashMap<String, String> updateBlobs,
                           HashMap<String, String> conflicts) {
        for (Map.Entry<String, String> kv: currBlobs.entrySet()) {
            String fileName = kv.getKey();
            String currSha1 = kv.getValue();
            boolean splitContain = splitCommitBlobs.containsKey(fileName);
            boolean otherContain = otherBlobs.containsKey(fileName);
            if (!splitContain && !otherContain) {
                updateBlobs.put(fileName, currSha1);
            }
            if (splitContain && !otherContain) {
                String splitSha1 = splitCommitBlobs.get(fileName);
                boolean currUnchanged = currBlobs.get(fileName).equals(
                        splitSha1);
                if (currUnchanged) {
                    rmBlobs.put(fileName, currSha1);
                }
            }
            if (splitContain && !otherContain) {
                String splitSha1 = splitCommitBlobs.get(fileName);
                boolean currUnchanged = currBlobs.get(fileName).equals(
                        splitSha1);
                if (!currUnchanged) {
                    updateConflict(currBlobs, otherBlobs, conflicts, fileName);
                }
            }
        }
    }
    /** handle merge according to different cases.
     * Given OTHER, OTHERBLOBS, SPLITCOMMITBLOBS, CURRBLOBS, RMBLOBS,
     * UPDATEBLOBS, CONFLICTS.
     * 1. merge modifications in OTHER, xor in CURRENT
     * 2. modified in both?
     * 3. not present in split, but in current, remain as they are. ???
     * 4. any files not present in split, current, but present in other:
     *      checked out, and staged.
     * 5. Any files present at the split point, unmodified in the given branch,
     *    and absent in the current branch should remain absent.
     * 6. conflicts.
     * 7. Any files that have been modified in the current branch but not
     *    in the given branch since the split point should stay as they are.
     */
    public void handleMerge(Commit other,
                            HashMap<String, String> otherBlobs,
                            HashMap<String, String> splitCommitBlobs,
                            HashMap<String, String> currBlobs,
                            HashMap<String, String> rmBlobs,
                            HashMap<String, String> updateBlobs,
                            HashMap<String, String> conflicts) {
        mergePart1(other, otherBlobs, splitCommitBlobs,
                currBlobs, updateBlobs, conflicts);
        mergePart2(otherBlobs, splitCommitBlobs,
                currBlobs, rmBlobs, updateBlobs, conflicts);
    }

    /** modify file in conflicts with format:
     * <<<<<<< HEAD
     * contents of file in current branch
     * =======
     * contents of file in given branch
     * >>>>>>>
     * define CONFLICTS:
     * the contents of both are changed and different from other.
     * or the contents of one are changed and the other file is deleted,
     * or the file was absent at the split point and
     * has different contents in the given and current branches.
     * given CURRBLOBS, OTHERBLOBS, FILENAME.
     */
    public void updateConflict(HashMap<String, String> currBlobs,
                               HashMap<String, String> otherBlobs,
                               HashMap<String, String> conflicts,
                               String fileName) {
        String header = "<<<<<<< HEAD" + "\n";
        boolean currContain = currBlobs.containsKey(fileName);
        boolean otherContain = otherBlobs.containsKey(fileName);
        if (currContain) {
            Blob blob = (Blob) Main.read(Main.getBlobPath(),
                    currBlobs.get(fileName));
            header += blob.getContents();
        }

        header += "=======" + "\n";
        if (otherContain) {
            Blob blob = (Blob) Main.read(Main.getBlobPath(),
                    otherBlobs.get(fileName));
            header += blob.getContents();
        }

        header += ">>>>>>>" + "\n";
        conflicts.put(fileName, header);
    }

    /** return all branches. */
    public HashMap<String, String> getAllBranches() {
        return _allBranches;
    }

    /** for checkout branch, remove files that are in the current branch but.
     * are not in the checkedout branch with COMMITID.
     * blobs: all blobs made by COMMITID the checkout branch
     */
    public void removeIfNotTracked(String commitID) {
        Commit commit = (Commit) Main.read(Main.getCommitPath(), commitID);
        HashMap<String, String> blobs = commit.getAllBlobs();
        Set<String> updateNames =  blobs.keySet();
        Staging stage = Main.getStaging();
        stage.stagedAllTracked();
        Set<String> untrackedFileNames = stage.getUntracked().keySet();
        Set<String> modified = stage.getModified().keySet();
        Set<String> untrackedOverlap = new HashSet<>(updateNames);
        Set<String> modifiedOverlap = new HashSet<>(updateNames);
        untrackedOverlap.retainAll(untrackedFileNames);
        modifiedOverlap.retainAll(modified);
        if (!untrackedOverlap.isEmpty() || !modifiedOverlap.isEmpty()) {
            System.out.println("There is an untracked file in the way;"
                                       + " delete it or add it first.");
            System.exit(0);
            return;
        }
        Set<String> currentNames = getCurrentCommit().getAllBlobs().keySet();
        for (String name: currentNames) {
            File f = Paths.get(Main.getWorkingDir(), name).toFile();
            Utils.restrictedDelete(f);
        }
    }

    /** checkout BRANCH. */
    public void checkoutBranch(String branch) {
        if (!getAllBranches().containsKey(branch)) {
            System.out.println("No such branch exists.");
        } else if (_currentBranch.equals(branch)) {
            System.out.println("No need to checkout the current branch. ");
        } else {
            String commitID = getAllBranches().get(branch);
            Commit commit = (Commit) Main.read(Main.getCommitPath(), commitID);
            HashMap<String, String> blobs = commit.getAllBlobs();
            Set<String> updateNames =  blobs.keySet();
            removeIfNotTracked(commitID);
            for (String update: updateNames) {
                Blob blob = (Blob) Main.read(Main.getBlobPath(),
                        blobs.get(update));
                String contents = blob.getContents();
                File workFile = Paths.get(Main.getWorkingDir(),
                        update).toFile();
                Utils.writeContents(workFile, contents);
            }
            _currentBranch = branch;
            Main.getStaging().setBlobs(commit);
            Main.getStaging().clearAll();
        }
    }


}
