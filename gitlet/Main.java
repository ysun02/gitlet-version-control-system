package gitlet;

import java.io.Serializable;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Yuan Sun
 */
public class Main {
    /** working dir. */
    private static String _workingDir = System.getProperty("user.dir");

    /** commitsTree. */
    private static CommitsTree _allCommits = null;
    /** Staging. */
    private static Staging _allStages = null;

    /** .gitlet. */
    private static Path _gitPath;
    /** .commits. */
    private static Path _commitPath;
    /** .blobs. */
    private static Path _blobPath;
    /** .tempBlob. */
    private static Path _tempBlobPath;

    /** files in cwd justAdded. */
    private static HashSet<String> _justAdded
            = new HashSet<>();

    /** files in cwd justModified. */
    private static HashSet<String> _justModified
            = new HashSet<>();

    /** files in cwd just Deleted. */
    private static HashSet<String> _justDeleted
            = new HashSet<>();

    /** link remote-name with local .git dir. */
    private static HashMap<String, String> _login
            = new HashMap<>();

    /** link remote-name with remote-dir. */
    private static HashMap<String, String> _remoteDir
            = new HashMap<>();

    /** return just modified. */
    public static HashSet<String> getJustModified() {
        return _justModified;
    }

    /** return just deleted. */
    public static HashSet<String> getJustDeleted() {
        return _justDeleted;
    }

    /** check modified on disk.
     * given FILENAMES. */
    public static void modifedOnDisk(
            List<String> fileNames) {
        Commit current = _allCommits.getCurrentCommit();
        HashMap<String, String> allTracked = current.getAllBlobs();
        HashMap<String, String> addStage = _allStages.getAddStage();
        for (String fileName: fileNames) {
            if (allTracked.containsKey(fileName)
                && !addStage.containsKey(fileName)) {
                String workingContents = Utils.readContentsAsString(
                        Utils.join(_workingDir, fileName));
                String sha1 = allTracked.get(fileName);
                Blob blob = (Blob) read(getBlobPath(), sha1);
                String contents = blob.getContents();
                String logContents = Utils.readContentsAsString(
                        Utils.join(Main.getLog(), fileName));

                if (!contents.equals(workingContents)
                    && !logContents.equals(workingContents)) {
                    _justModified.add(fileName);
                }
            }
        }
    }

    /** return files. */
    public static HashSet<String> getNewFiles() {
        return _justAdded;
    }

    /** initDir. */
    public static void initDir() {

        if (_gitPath != null) {
            String errorMessage =
                    "A Gitlet version-control system already exists"
                                          + " in the current directory.";
            System.out.println(errorMessage);
            System.exit(0);
        }
        _gitPath = Paths.get(_workingDir, ".gitlet");
        _commitPath = Paths.get(String.valueOf(_gitPath), ".commits");
        _blobPath = Paths.get(String.valueOf(_gitPath), ".blobs");
        _tempBlobPath = Paths.get(String.valueOf(_gitPath), ".tempBlobs");
        _gitPath.toFile().mkdirs();
        _commitPath.toFile().mkdirs();
        _blobPath.toFile().mkdirs();
        _tempBlobPath.toFile().mkdirs();
    }

    /** gitlet init. */
    public static void init() {
        initDir();
        _allCommits = new CommitsTree();
        _allStages = new Staging();
        _allStages.setBlobs(_allCommits.getCurrentCommit());
    }

    /** add FILENAME. */
    public static void add(String fileName) {
        File f = Utils.join(getWorkingDir(), fileName);
        if (!f.exists()) {
            System.out.println("file does not exist.");
            System.exit(0);
            return;
        }
        _allStages.add(fileName, _allCommits.getCurrentCommit());
    }

    /** commit MSG. */
    public static void commit(String msg) {
        if (msg.equals("")) {
            System.out.println("Please enter a commit message.");
            System.exit(0);
            return;
        }
        HashMap<String, String> add = _allStages.getAddStage();
        HashMap<String, String> del = _allStages.getRemoveStage();
        HashMap<String, String> all = _allStages.getTrackedBlobs();
        if (add.isEmpty() && del.isEmpty()) {
            System.out.println("No changes added to the commit.");
        } else {
            _allCommits.addCommit(msg, "", add, del);
        }
    }

    /** rm FILENAME. */
    public static void rm(String fileName) {
        _allStages.rm(fileName, _allCommits.getCurrentCommit());
    }

    /** rm-branch NAME. */
    public static void rmBranch(String name) {
        _allCommits.rmBranch(name);
    }

    /** log. */
    public static void log() {
        Main._allCommits.log();
    }

    /** global-log. */
    public static void globalLog() {
        Main._allCommits.globalLog();
    }

    /** status. */
    public static void status() {
        List<String> cwd = Utils.plainFilenamesIn(
                _workingDir);
        modifedOnDisk(cwd);
        HashMap<String, String> allBlobs =
                _allStages.getTrackedBlobs();
        HashMap<String, String> removed = _allStages.getRemoveStage();
        List<String> works = Utils.plainFilenamesIn(Main.getWorkingDir());
        Main._allCommits.status();
        Main._allStages.status();
    }

    /** find MSG. */
    public static void find(String msg) {
        Main._allCommits.findMsg(msg);
    }

    /** return .gitlet path. */
    public static String getGitPath() {
        return _gitPath.toString();
    }

    /** return .commit path. */
    public static String getCommitPath() {
        return _commitPath.toString();
    }

    /** return .blobs path. */
    public static String getBlobPath() {
        return _blobPath.toString();
    }

    /** return .tempBlobs path. */
    public static String getTempBlobPath() {
        return _tempBlobPath.toString();
    }

    /** return working path. */
    public static String getWorkingDir() {
        return _workingDir;
    }

    /** return CommitsTree. */
    public static CommitsTree getAllCommits() {
        return _allCommits;
    }

    /** return All Stages. */
    public static Staging getStaging() {
        return _allStages;
    }

    /** set Staging to STAGE. */
    public static void setStaging(Staging stage) {
        _allStages = stage;
    }

    /** write OBJ to FILENAME in SUPERPATH. */
    public static void writeObjToFile(String superPath,
                                      String fileName, Serializable obj) {
        File file = Paths.get(superPath, fileName).toFile();
        Utils.writeObject(file, obj);
    }

    /** checkout ARGS. */
    public static void checkoutCommand(String[] args) {
        if (args.length == 3 && args[1].equals("--")) {
            checkoutFile(args[2]);
        } else if (args.length == 4 && args[2].equals("--")) {
            checkoutCommit(args[1], args[3]);
        } else if (args.length == 2) {
            checkoutBranch(args[1]);
        } else {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }

    }
    /** r commands in ARGS. */
    public static void runCommands(String[] args) {
        if (args.length < 1) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String command = args[0];
        if (command.equals("init")) {
            init();
        } else if (!Utils.join(_workingDir, ".gitlet").exists()
                   && _gitPath == null) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        } else if (command.equals("add") && args.length == 2) {
            add(args[1]);
        } else if (command.equals(("commit")) && args.length == 2) {
            commit(args[1]);
        } else if (command.equals("rm")) {
            rm(args[1]);
        } else if (command.equals("checkout")) {
            checkoutCommand(args);
        } else if (command.equals("log")) {
            log();
        } else if (command.equals("global-log")) {
            globalLog();
        } else if (command.equals("find") && args.length == 2) {
            find(args[1]);
        } else if (command.equals("status")) {
            status();
        } else if (command.equals("branch") && args.length == 2) {
            branch(args[1]);
        } else if (command.equals("rm-branch") && args.length == 2) {
            rmBranch(args[1]);
        } else if (command.equals("reset") && args.length == 2) {
            reset(args[1]);
        } else if (command.equals("merge") && args.length == 2) {
            merge(args[1]);
        } else {
            runRemoteCommands(args);
        }
    }

    /** fetch REMOTENAME BRANCHNAME. */
    public static void fetch(String remoteName, String branchName) {
        String remotePath = getRemoteDir().get(remoteName);
        File f = Paths.get(remotePath, ".gitlet").toFile();
        if (!f.exists()) {
            System.out.println("Remote directory not found.");
            System.exit(0);
            return;
        }
        _allCommits.fetch(remotePath,
                remoteName, branchName);
    }

    /** Given REMOTENAME, return remoteTrees. */
    public static CommitsTree getRemoteTree(String remoteName) {
        String remotePath = getRemoteDir().get(remoteName);
        File treeConfig = Paths.get(
                remotePath, "commitsConfig.bin").toFile();

        CommitsTree remoteTrees = Utils.readObject(
                treeConfig, CommitsTree.class);
        return remoteTrees;
    }

    /** return .blobs under REMOTEPATH. */
    public static String getRemoteBlobs(String remotePath) {
        remotePath = Paths.get(remotePath, ".gitlet").toString();
        return Utils.join(remotePath, ".blobs").toString();
    }

    /** return allBranches in REMOTETREES. */
    public static HashMap<String, String> getAllRemoteBranches(
            CommitsTree remoteTrees
    ) {
        HashMap<String, String> allBranches = remoteTrees.getAllBranches();
        return allBranches;
    }

    /** return Remote commits path REMOTEPATH. */
    public static String getRemoteCommitsPath(String remotePath) {
        remotePath = Paths.get(remotePath, ".gitlet").toString();
        return Paths.get(remotePath, ".commits").toString();
    }

    /** going remote with ARGS. */
    public static void runRemoteCommands(String[] args) {
        String command = args[0];
        if (command.equals("add-remote") && args.length == 3) {
            addRemote(args[1], args[2]);
        } else if (command.equals("rm-remote") && args.length == 2) {
            rmRemote(args[1]);
        } else if (command.equals("push") && args.length == 3) {
            push(args[1], args[2]);
        } else if (command.equals("fetch") && args.length == 3) {
            fetch(args[1], args[2]);
        } else if (command.equals("pull") && args.length == 3) {
            pull(args[1], args[2]);
        } else {
            System.out.println("No command with that name exists.");
            System.exit(0);
        }
    }

    /** pull REMOTENAME REMOTEBRANCH. */
    public static void pull(String remoteName, String remoteBranch) {
        String remotePath = getRemotePath(remoteName);
        _allCommits.pull(remotePath, remoteName, remoteBranch);
    }

    /** return remotePath given REMOTENAME. */
    public static String getRemotePath(String remoteName) {
        String remotePath = getRemoteDir().get(remoteName);
        return remotePath;
    }

    /** Return all converted forward slahes to PATH separators. */
    public static String convertToSlash(String path) {
        String legalPath = path.replaceAll("/", File.separator);
        return legalPath;
    }




    /** command for push [REMOTENAME] [BRANCHNAME].
     */
    public static void push(String remoteName, String branchName) {
        if (!getLogin().containsKey(remoteName)
            && !getRemoteDir().containsKey(remoteName)) {
            System.out.println("Remote directory not found.");
            System.exit(0);
            return;
        }
        String remotePath = getRemoteDir().get(remoteName);
        File f = Paths.get(remotePath, ".gitlet").toFile();
        if (!f.exists()) {
            System.out.println("Remote directory not found.");
            System.exit(0);
            return;
        }
        _allCommits.push(remotePath, remoteName,
                branchName);
    }

    /** command for add-remote [REMOTENAME]
     *                          [name of REMOTEDIR]/.gitlet.
     */
    public static void addRemote(String remoteName, String remoteDir) {
        if (getLogin().containsKey(remoteName)
            || getRemoteDir().containsKey(remoteName)) {
            System.out.println("A remote with that name already exists.");
            System.exit(0);
            return;
        }
        String path = convertToSlash(remoteDir);
        String remotePath = Paths.get(_workingDir,
                "remote").toString();
        Utils.writeContents(Paths.get(remotePath, remoteName).toFile(), path);
        _login.put(remoteName, getGitPath());
        _remoteDir.put(remoteName, remoteDir);
    }

    /** command for rm-remote [REMOTENAME]. */
    public static void rmRemote(String remoteName) {
        if (!getLogin().containsKey(remoteName)
            && !getRemoteDir().containsKey(remoteName)) {
            System.out.println("e: A remote with that name does not exist.");
            System.exit(0);
            return;
        }
        String remotePath = Paths.get(_workingDir,
                "remote").toString();
        File remoteRecord = Paths.get(
                remotePath, remoteName).toFile();
        remoteRecord.delete();
        _login.remove(remoteName);
        _remoteDir.remove(remoteName);

    }

    /** return remote login info. */
    public static HashMap<String, String> getLogin() {
        return _login;
    }
    /** return remoteDir storage. */
    public static HashMap<String, String> getRemoteDir() {
        return _remoteDir;
    }

    /** command for merge BRANCHNAME. */
    public static void merge(String branchName) {
        _allCommits.merge(branchName);
    }

    /** command for reset COMMITID. */
    public static void reset(String commitID) {
        _allCommits.reset(commitID);
    }

    /** branch NAME. */
    public static void branch(String name) {
        _allCommits.branch(name);
    }

    /** checkout BRANCH. */
    public static void checkoutBranch(String branch) {
        _allCommits.checkoutBranch(branch);
    }

    /** checkout COMMITID FILENAME. */
    public static void checkoutCommit(String commitID, String fileName) {
        _allCommits.checkoutCommit(commitID, fileName);
    }

    /** checkout -- FILENAME. */
    public static void checkoutFile(String fileName) {
        _allCommits.checkoutFile(fileName);
    }

    /** Return all files read in a PATH. */
    public static ArrayList<Object> readAll(String path) {
        ArrayList<Object> objs = new ArrayList<>();
        List<String> names = Utils.plainFilenamesIn(path);
        for (String name: names) {
            objs.add(read(path, name));
        }
        return objs;
    }

    /** Return the object read, given PATH and FILENAME. */
    public static Object read(String path, String fileName) {
        Object obj = null;
        String file = Utils.join(path, fileName).toString();
        File inFile = new File(file);
        try {
            ObjectInputStream inp =
                    new ObjectInputStream(new FileInputStream(inFile));
            obj = inp.readObject();
            inp.close();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("IO except" + e.getMessage());
        }
        return obj;
    }

    /** Write OBJ with FILENAME to a PATH. */
    public static void write(String path, String fileName, Object obj) {
        File f = Utils.join(path, fileName);
        String file = f.toString();
        File outFile = new File(file);
        try {
            ObjectOutputStream out =
                    new ObjectOutputStream(new FileOutputStream(outFile));
            out.writeObject(obj);
            out.close();
        } catch (IOException e) {
            System.out.println("IO except" + e.getMessage());
        }
    }

    /** check deleted upon FILES and CWD. */
    public static void checkDeleted(HashSet<String> cwd,
                                    List<String> files) {
        if (_allStages == null) {
            System.out.println("Not in an initialized Gitlet directory.");System.exit(0);
            return;
        }
        HashMap<String, String> delStaged =
                _allStages.getRemoveStage();
        HashMap<String, String> allTracked =
                _allStages.getTrackedBlobs();
        for (String file: files) {
            if (!cwd.contains(file)
                && !delStaged.containsKey(file)
                && allTracked.containsKey(file)) {
                _justDeleted.add(file);
            }
        }
    }

    /** return lastTime log dir. */
    public static File getLog() {
        File lastTime = Paths.get(_workingDir, "logs").toFile();
        return lastTime;
    }

    /** setup helper. */
    public static void setUp() {
        _allCommits = (CommitsTree) Main.read(_workingDir,
                "commitsConfig.bin");
        _allStages = (Staging) Main.read(_workingDir,
                "stagingConfig.bin");
        _gitPath = Paths.get(_workingDir,
                ".gitlet");
        _commitPath = Paths.get(String.valueOf(_gitPath),
                ".commits");
        _blobPath = Paths.get(String.valueOf(_gitPath),
                ".blobs");
        _tempBlobPath = Paths.get(String.valueOf(_gitPath),
                ".tempBlobs");
    }

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) {
        try {
            File commitsConfigFile = Paths.get(_workingDir,
                    "commitsConfig.bin").toFile();
            File stagingConfigFile = Paths.get(_workingDir,
                    "stagingConfig.bin").toFile();
            File remoteFile = Paths.get(_workingDir,
                    "remote").toFile();
            HashSet<String> cwd =
                    new HashSet<>(Utils.plainFilenamesIn(_workingDir));
            File lastTime = Paths.get(_workingDir, "logs").toFile();
            if (commitsConfigFile.exists() && stagingConfigFile.exists()) {
                setUp();
            }
            if (lastTime.exists()) {
                List<String> files = Utils.plainFilenamesIn(lastTime);
                for (String file: cwd) {
                    if (!files.contains(file)) {
                        _justAdded.add(file);
                    }
                }
                checkDeleted(cwd, files);
            } else {
                lastTime.mkdirs();
                _justAdded = cwd;
            }
            if (remoteFile.exists()) {
                List<String> remoteNames = Utils.plainFilenamesIn(remoteFile);
                for (String name: remoteNames) {
                    File f = Utils.join(remoteFile, name);
                    String remoteLoc = Utils.readContentsAsString(f);
                    remoteLoc = remoteLoc.substring(0,
                            remoteLoc.length() - 8);
                    _remoteDir.put(name, remoteLoc);
                }
            } else {
                remoteFile.mkdirs();
            }
            runCommands(args);
            write(_workingDir, "commitsConfig.bin", _allCommits);
            write(_workingDir, "stagingConfig.bin", _allStages);
            cwd = new HashSet<>(Utils.plainFilenamesIn(_workingDir));
            List<String> files = Utils.plainFilenamesIn(lastTime);
            for (String file: files) {
                if (!cwd.contains(file)) {
                    File f = Utils.join(lastTime, file);
                    f.delete();
                }
            }
            for (String file: cwd) {
                File f = Paths.get(_workingDir, file).toFile();
                File log = getLog();
                File exact = Utils.join(log, file);
                String contents = Utils.readContentsAsString(f);
                Utils.writeContents(exact, contents);
            }
        } catch (NullPointerException e) {
            System.out.println(e);
        }
    }

}
