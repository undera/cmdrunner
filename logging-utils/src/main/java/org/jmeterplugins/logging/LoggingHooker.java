package org.jmeterplugins.logging;

import java.lang.reflect.Constructor;

public class LoggingHooker {

    public LoggingHooker() {
        if (isJMeter32orLater()) {
            configureCMDLogging();
        }
    }

    private void configureCMDLogging() {
        try {
            Class cls = Thread.currentThread().getContextClassLoader().loadClass("org.jmeterplugins.logging.LoggingConfigurator");
            Constructor constructor = cls.getConstructor();
            constructor.newInstance();
        } catch (Throwable ex) {
            System.out.println("Fail to configure logging " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
    }

    public static boolean isJMeter32orLater() {
        try {
            Class<?> cls = Thread.currentThread().getContextClassLoader().loadClass("org.apache.jmeter.gui.logging.GuiLogEventBus");
            if (cls != null) {
                return true;
            }
        } catch (ClassNotFoundException ex) {
//            log.debug("Class 'org.apache.jmeter.gui.logging.GuiLogEventBus' not found", ex);
        } catch (Throwable ex) {
            System.out.println("Fail to detect JMeter version " + ex.getMessage());
            ex.printStackTrace(System.out);
        }
        return false;
    }
}
