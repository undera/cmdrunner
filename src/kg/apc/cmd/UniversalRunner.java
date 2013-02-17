package kg.apc.cmd;

// N.B. this must only use standard Java packages
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Main class for CLI - sets up initial classpath and the loader. I took it from
 * JMeter, yes, but I changed it a lot.
 */

/// FIXME: the code looks so poorly structured, refactor it!!!
public final class UniversalRunner {

    private static final String CLASSPATH_SEPARATOR = System.getProperty("path.separator");
    private static final String OS_NAME = System.getProperty("os.name");
    private static final String OS_NAME_LC = OS_NAME.toLowerCase(java.util.Locale.ENGLISH);
    private static final String JAVA_CLASS_PATH = "java.class.path";
    private static final String jarDirectory;
    private static final String ADDITIONAL_CP = "additional.classpath";
    private static final FilenameFilter jarFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {// only accept jar files
            return name.endsWith(".jar");
        }
    };

    static {
        System.setProperty("java.awt.headless", "true");
        List jars = new LinkedList();
        StringBuffer classpath = new StringBuffer();

        String initial_classpath = System.getProperty(JAVA_CLASS_PATH);
        String additional = System.getProperty(ADDITIONAL_CP);
        if (additional != null) {
            initial_classpath = initial_classpath + CLASSPATH_SEPARATOR + additional;
            String[] parts = additional.split(CLASSPATH_SEPARATOR);
            for (int n = 0; n < parts.length; n++) {
                File[] f = {new File(parts[n])};
                if (f[0].isDirectory()) {
                    f = f[0].listFiles(jarFilter);
                }
                addFiles(f, jars, classpath);
            }
        }
        jarDirectory = getJarDirectory(initial_classpath);
        // Add standard jar locations to initial classpath
        buildUpdatedClassPath(jars, classpath);

        // ClassFinder needs the classpath
        String cp = classpath.toString();
        System.setProperty(JAVA_CLASS_PATH, cp);
        System.err.println(cp);
        //p.list(System.err);

        URL[] urls = (URL[]) jars.toArray(new URL[0]);
        URLClassLoader loader = new URLClassLoader(urls);
        Thread.currentThread().setContextClassLoader(loader);
    }

    private static String getJarDirectory(final String initial_classpath) {
        // Find JMeter home dir from the initial classpath
        String tmpDir = null;
        StringTokenizer tok = new StringTokenizer(initial_classpath, File.pathSeparator);
        //System.err.println("CP: "+initial_classpath);
        if (tok.countTokens() == 1
                || (tok.countTokens() == 2 // Java on Mac OS can add a second entry to the initial classpath
                && OS_NAME_LC.startsWith("mac os x"))) {
            File jar = new File(tok.nextToken());
            try {
                tmpDir = jar.getCanonicalFile().getParent();
                //System.err.println("Can: "+tmpDir);
            } catch (IOException e) {
            }
        } else {// e.g. started from IDE with full classpath
            File userDir = new File(System.getProperty("user.dir"));
            tmpDir = userDir.getAbsolutePath();
        }
        return tmpDir;
    }

    private static StringBuffer buildUpdatedClassPath(List jars, StringBuffer classpath) {
        List libDirs = new LinkedList();
        File f = new File(jarDirectory);
        while (f != null) {
            libDirs.add(f.getAbsoluteFile());
            f = f.getParentFile();
        }

        Iterator it = libDirs.iterator();

        while (it.hasNext()) {
            File libDir = (File) it.next();
            File[] libJars = libDir.listFiles(jarFilter);

            if (libJars == null) {
                new Throwable("Could not access " + libDir).printStackTrace(System.err);
                continue;
            }
            addFiles(libJars, jars, classpath);
        }
        return classpath;
    }

    private static void addFiles(File[] libJars, List jars, StringBuffer classpath) {
        /*
         * Does the system support UNC paths? If so, may need to fix them up
         * later
         */
        boolean usesUNC = OS_NAME_LC.startsWith("windows");

        for (int i = 0; i < libJars.length; i++) {
            try {
                String s = libJars[i].getPath();

                // Fix path to allow the use of UNC URLs
                if (usesUNC) {
                    if (s.startsWith("\\\\") && !s.startsWith("\\\\\\")) {
                        s = "\\\\" + s;
                    } else if (s.startsWith("//") && !s.startsWith("///")) {
                        s = "//" + s;
                    }
                } // usesUNC

                jars.add(new File(s).toURI().toURL());// See Java bug 4496398
                classpath.append(CLASSPATH_SEPARATOR);
                classpath.append(s);
                //System.err.println(s);
            } catch (MalformedURLException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Prevent instantiation.
     */
    private UniversalRunner() {
    }

    /**
     * Get the directory where CMD jar is placed. This is the absolute path
     * name.
     *
     * @return the directory where JMeter is installed.
     */
    public static String getJARLocation() {
        return jarDirectory;
    }

    /**
     * The main program which actually runs JMeter.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Throwable {
        try {
            Class initialClass;
            // make it independent - get class name & method from props/manifest
            initialClass = Thread.currentThread().getContextClassLoader().loadClass("kg.apc.cmdtools.PluginsCMD");
            Object instance = initialClass.newInstance();
            Method startup = initialClass.getMethod("processParams", new Class[]{(new String[0]).getClass()});
            Object res = startup.invoke(instance, new Object[]{args});
            int rc = ((Integer) res).intValue();
            if (rc != 0) {
                System.exit(rc);
            }
        } catch (Throwable e) {
            if (e.getCause() != null) {
                System.err.println("ERROR: " + e.getCause().toString());
                System.err.println("*** Problem's technical details go below ***");
                System.err.println("Home directory was detected as: " + jarDirectory);
                throw e.getCause();
            } else {
                System.err.println("Home directory was detected as: " + jarDirectory);
                throw e;
            }
        }
    }
}
