package com.jkys;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class GenerTest {


    @Test
    public void testClassPath() {

        File file = new File("jar:file:/C:/Users/ks/Desktop/测试/dts-admin-1.0-SNAPSHOT.jar!/BOOT-INF/classes!/");
        if (file.exists()) {
            File[] fs = file.listFiles();
            for (File f : fs) {
                System.out.println(f.getName());
            }
        } else {
            System.out.println("not exists");
        }

    }

}
