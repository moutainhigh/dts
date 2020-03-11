package com.jkys.job;

import org.springframework.boot.ApplicationHome;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author xuxueli 2018-10-28 00:38:13
 */
@SpringBootApplication
public class JobAdminApplication {

	public static void main(String[] args) {
		String dir = System.getProperty("user.dir");
		ApplicationHome applicationHome = new ApplicationHome(JobAdminApplication.class);
		File file = applicationHome.getSource();
		if (file != null) {
			exactPlugins(file.getAbsolutePath(),dir);
		}
        SpringApplication.run(JobAdminApplication.class, args);
	}

	public static void exactPlugins(String src, String dst) {
		File srcFile = new File(src);
		if (!srcFile.exists() || srcFile.isDirectory()) {
			return;
		}
		File pluginDir = new File(dst + File.separator + "BOOT-INF");
		if (!pluginDir.exists()) {
			ZipInputStream zis = null;
			FileOutputStream fos = null;
			try {
				FileInputStream fis = new FileInputStream(srcFile);
				zis = new ZipInputStream(fis);
				ZipEntry zipEntry;
				int len;
				byte[] bytes = new byte[1024];
				while ((zipEntry = zis.getNextEntry()) != null) {
					if (zipEntry.getName().startsWith("BOOT-INF/classes/plugins")) {
						File file = new File(dst + File.separator + zipEntry.getName());
						if (zipEntry.isDirectory()) {
							if (!file.exists()) {
								file.mkdirs();
							}

						} else {
							if (!file.exists()) {
								if (!file.getParentFile().exists()) {
									file.getParentFile().mkdirs();
								}
								file.createNewFile();
								fos = new FileOutputStream(file);
								while ((len = zis.read(bytes)) != -1) {
									fos.write(bytes,0,len);
								}
								fos.close();

							}

						}

					}

					zis.closeEntry();
				}
				zis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}