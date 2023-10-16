package org.glavo.japp.launcher;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Launcher {
    private static Path getBootLauncher() throws URISyntaxException {
        return Paths.get(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    public static void main(String[] args) throws Throwable {
        if (args.length < 1) {
            throw new UnsupportedOperationException("TODO: Help message");
        }

        String javaName = System.getProperty("os.name").contains("Win") ? "java.exe" : "java";

        List<String> command = new ArrayList<>();
        Collections.addAll(command,
                Paths.get(System.getProperty("java.home"), "bin", javaName).toString(),
                "--module-path",
                getBootLauncher().toString(),
                "-Dorg.glavo.japp.file=" + args[0],
                "--add-exports=java.base/jdk.internal.loader=org.glavo.japp",
                "--add-exports=java.base/jdk.internal.module=org.glavo.japp",
                "--add-opens=java.base/jdk.internal.loader=org.glavo.japp",
                "--module",
                "org.glavo.japp/org.glavo.japp.launcher.BootLauncher");

        for (int i = 1; i < args.length; i++) {
            command.add(args[i]);
        }

        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }
}