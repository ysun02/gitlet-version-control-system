package gitlet;

import org.junit.Test;
import ucb.junit.textui;

import java.io.File;
import java.nio.file.Paths;

/** The suite of all JUnit tests for the gitlet package.
 *  @author
 */
public class UnitTest {

    /** Run the JUnit tests in the loa package. Add xxxTest.class entries to
     *  the arguments of runClasses to run other JUnit tests. */
    public static void main(String[] ignored) {
        textui.runClasses(UnitTest.class);
    }

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }
    /** A dummy test to avoid complaint. */
    @Test
    public void test02BasicCheckout() {
        String workingDir = System.getProperty("user.dir");
        File f  = Paths.get(workingDir, ".gitlet").toFile();
        File hello = Paths.get(workingDir, "hello.txt").toFile();

        if (f.exists()) {
            deleteDir(f);
        }
        Main.init();
        Utils.writeContents(hello, "hello");
        Main.add("hello.txt");
        Main.commit("commit me");
        Utils.writeContents(hello, "1234");
        Main.checkoutFile("hello.txt");
        System.out.println("stop and check the fields");
    }

}


