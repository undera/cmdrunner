package kg.apc.cmd;

// N.B. this must only use standard Java packages

import java.io.File;
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;

public final class UniversalRunner {

    public static final String JAVA_CLASS_PATH = "java.class.path";
    private static final String jarDirectory;
    private static final FilenameFilter jarFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {// only accept jar files
            return name.endsWith(".jar");
        }
    };

    static {
        System.setProperty("java.awt.headless", "true");
        StringBuffer classpath = new StringBuffer();

        File self = new File(UniversalRunner.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        jarDirectory = decodePath(self.getParent());
        // Add standard jar locations to initial classpath
        List<URL> jars = buildUpdatedClassPath(jarDirectory, classpath);

        String cp = classpath.toString();
        System.setProperty(JAVA_CLASS_PATH, cp);

        URL[] urls = jars.toArray(new URL[0]);
        URLClassLoader loader = new URLClassLoader(urls);
        Thread.currentThread().setContextClassLoader(loader);

        System.setProperty("log4j.configurationFile", new File(decodePath(self.getParentFile().getParent()), "bin/log4j2.xml").getAbsolutePath());
    }

    private static String decodePath(String path) {
        try {
            return URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.out.println("Failed decode path: " + path);
            e.printStackTrace(System.out);
            return path;
        }
    }

    private static List<URL> buildUpdatedClassPath(String jarDir, StringBuffer classpath) {
        List<URL> jars = new LinkedList<URL>();
        List<File> libDirs = new LinkedList<File>();
        File f = new File(jarDir);
        while (f != null) {
            libDirs.add(f.getAbsoluteFile());
            f = f.getParentFile();
        }

        // add lib subdir
        f = new File(jarDir + File.separator + "ext");
        if (f.exists()) {
            libDirs.add(f.getAbsoluteFile());
        }

        for (File libDir : libDirs) {
            File[] libJars = libDir.listFiles(jarFilter);

            if (libJars == null) {
                continue;
            }
            addFiles(libJars, jars, classpath);
        }
        return jars;
    }

    private static void addFiles(File[] libJars, List<URL> jars, StringBuffer classpath) {
        for (File libJar : libJars) {
            try {
                String s = libJar.getPath();

                jars.add(new File(s).toURI().toURL());
                classpath.append(File.pathSeparator);
                classpath.append(s);
                //System.err.println(s);
            } catch (MalformedURLException e) {
                e.printStackTrace(System.err);
            }
        }
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
            Class<?> initialClass;
            // make it independent - get class name & method from props/manifest
            initialClass = Thread.currentThread().getContextClassLoader().loadClass("kg.apc.cmdtools.PluginsCMD");
            Object instance = initialClass.newInstance();
            Method startup = initialClass.getMethod("processParams", (new String[0]).getClass());
            Object res = startup.invoke(instance, new Object[]{args});
            int rc = (Integer) res;
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
