package org.glavo.japp.packer;

import org.glavo.japp.boot.JAppResource;
import org.glavo.japp.boot.JAppResourceGroup;
import org.glavo.japp.packer.compressor.CompressResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LocalClassPathProcessor extends ClassPathProcessor {

    public static final LocalClassPathProcessor INSTANCE = new LocalClassPathProcessor();

    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";

    private static long timeToMillis(FileTime time) {
        if (time == null) {
            return JAppResource.NO_TIME;
        }

        long millis = time.toMillis();
        if (millis == JAppResource.NO_TIME) {
            throw new IllegalArgumentException("Invalid time: " + time);
        }
        return millis;
    }

    public static void addJar(JAppPacker packer, Path jar, boolean isModulePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            Attributes attributes = null;

            ZipEntry manifestEntry = zipFile.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry != null) {
                try (InputStream input = zipFile.getInputStream(manifestEntry)) {
                    attributes = new Manifest(input).getMainAttributes();
                }
            }

            boolean multiRelease;
            String moduleName = null;

            if (attributes != null) {
                multiRelease = Boolean.parseBoolean(attributes.getValue("Multi-Release"));
                if (isModulePath) {
                    moduleName = attributes.getValue("Automatic-Module-Name");
                }
            } else {
                multiRelease = false;
            }

            if (isModulePath && moduleName == null) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();

                    String name = entry.getName();
                    if (name.equals("module-info.class")) {
                        // OK
                    } else if (multiRelease && name.startsWith(MULTI_RELEASE_PREFIX) && name.endsWith("/module-info.class")) {
                        // e.g. META-INF/versions/9/module-info.class
                        String[] elements = name.split("/");
                        if (elements.length != 4) {
                            continue;
                        }

                        try {
                            if (Integer.parseInt(elements[2]) < 9) {
                                continue;
                            }
                        } catch (NumberFormatException ignored) {
                            continue;
                        }

                    } else {
                        continue;
                    }

                    // parse module-info.class

                    try (InputStream mi = zipFile.getInputStream(entry)) {
                        moduleName = ModuleInfoReader.readModuleName(mi);
                    }
                }
            }

            if (isModulePath && moduleName == null) {
                moduleName = ModuleInfoReader.deriveAutomaticModuleName(jar.getFileName().toString());
            }

            JAppResourceGroup baseGroup = new JAppResourceGroup();
            TreeMap<Integer, JAppResourceGroup> multiGroups = multiRelease ? new TreeMap<>() : null;

            // Then write all entries

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.endsWith("/")) {
                    continue;
                }

                JAppResourceGroup group = baseGroup;

                if (multiRelease && name.startsWith(MULTI_RELEASE_PREFIX)) {
                    int idx = name.indexOf('/', MULTI_RELEASE_PREFIX.length());

                    if (idx > MULTI_RELEASE_PREFIX.length() && idx < name.length() - 1) {
                        String ver = name.substring(MULTI_RELEASE_PREFIX.length(), idx);
                        try {
                            int v = Integer.parseInt(ver);
                            if (v > 9) {
                                group = multiGroups.computeIfAbsent(v, n -> new JAppResourceGroup());
                                name = name.substring(idx + 1);
                            }

                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                byte[] buffer = new byte[Math.toIntExact(entry.getSize())];
                try (InputStream in = zipFile.getInputStream(entry)) {
                    int count = 0;
                    int n;
                    while ((n = in.read(buffer, count, buffer.length - count)) > 0) {
                        count += n;
                    }

                    assert count == buffer.length;
                }

                CompressResult result = packer.compressor.compress(packer, buffer, entry);

                group.put(name, new JAppResource(
                        name, packer.getCurrentOffset(), entry.getSize(),
                        result.getMethod(), result.getLength(),
                        timeToMillis(entry.getCreationTime()),
                        timeToMillis(entry.getLastModifiedTime()),
                        timeToMillis(entry.getLastAccessTime())
                ));

                packer.getOutput().writeBytes(result.getCompressedData(), result.getOffset(), result.getLength());
            }

            packer.addLocalReference(
                    isModulePath, isModulePath ? moduleName : jar.getFileName().toString(),
                    baseGroup, multiGroups
            );
        }
    }

    public static void addDir(JAppPacker packer, Path dir, boolean isModulePath) throws IOException {
        String name;
        if (isModulePath) {
            try (InputStream input = Files.newInputStream(dir.resolve("module-info.class"))) {
                name = ModuleInfoReader.readModuleName(input);
            }
        } else {
            name = null;
        }

        JAppResourceGroup group = new JAppResourceGroup();

        Path absoluteDir = dir.toAbsolutePath().normalize();
        Files.walkFileTree(absoluteDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String path = absoluteDir.relativize(file).toString().replace('\\', '/');
                byte[] data = Files.readAllBytes(file);
                CompressResult result = packer.compressor.compress(packer, Files.readAllBytes(file), file, attrs);
                ((Map<String, JAppResource>) group).put(path, new JAppResource(
                        path, packer.getCurrentOffset(), data.length,
                        result.getMethod(), result.getLength(),
                        timeToMillis(attrs.creationTime()),
                        timeToMillis(attrs.lastModifiedTime()),
                        timeToMillis(attrs.lastAccessTime())
                ));
                packer.getOutput().writeBytes(result.getCompressedData(), result.getOffset(), result.getLength());
                return FileVisitResult.CONTINUE;
            }
        });

        packer.addLocalReference(isModulePath, name, group, null);
    }

    @Override
    public void process(JAppPacker packer, String path, boolean isModulePath, Map<String, String> options) throws IOException {
        String name = options.remove("name");

        if (!options.isEmpty()) {
            throw new IllegalArgumentException("Unrecognized options: " + options.keySet());
        }

        boolean scanFiles = false;
        if (!isModulePath && (path.endsWith("/*") || path.endsWith("\\*"))) {
            scanFiles = true;
            path = path.substring(0, path.length() - 2);
        }

        Path p = Paths.get(path);
        BasicFileAttributes attributes = Files.readAttributes(p, BasicFileAttributes.class);

        if (scanFiles && !attributes.isDirectory()) {
            throw new IllegalArgumentException(path + " is not a directory");
        }

        if (attributes.isDirectory()) {
            if (isModulePath) {
                Path mi = p.resolve("module-info.class");
                if (!Files.exists(mi)) {
                    scanFiles = true;
                }
            }

            if (scanFiles) {
                if (name != null) {
                    throw new IllegalArgumentException("Name should not be set for multiple files");
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
                    for (Path file : stream) {
                        String fileName = file.getFileName().toString();

                        if (Files.isRegularFile(file) && fileName.endsWith(".jar")) {
                            addJar(packer, file, isModulePath);
                        }
                    }
                }
            } else {
                addDir(packer, p, isModulePath);
            }
        } else if (p.getFileName().toString().endsWith(".jar")) {
            addJar(packer, p, isModulePath);
        } else {
            throw new IllegalArgumentException("Unsupported file format: " + p);
        }
    }
}