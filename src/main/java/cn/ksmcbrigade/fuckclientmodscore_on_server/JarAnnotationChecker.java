package cn.ksmcbrigade.fuckclientmodscore_on_server;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarAnnotationChecker {
    public static boolean hasAnnotation(JarFile jarFile, JarEntry jarEntry, String annotation) throws IOException {
        if (!jarEntry.getName().endsWith(".class")) {
            return false;
        }

        String annotationDescriptor = "L" + annotation.replace('.', '/') + ";";

        try (InputStream inputStream = jarFile.getInputStream(jarEntry)) {
            byte[] classBytes = IOUtils.toByteArray(inputStream);

            return containsAnnotation(classBytes, annotationDescriptor);
        }
    }

    private static boolean containsAnnotation(byte[] classBytes, String annotationDesc) {
        byte[] annotationBytes = annotationDesc.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        return indexOf(classBytes, annotationBytes) != -1;
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int i = 0; i <= source.length - target.length; i++) {
            boolean found = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    public static boolean referencesMinecraftClient(JarFile jarFile, JarEntry entry) throws IOException {
        if (!entry.getName().endsWith(".class")) {
            return false;
        }

        try (InputStream is = jarFile.getInputStream(entry)) {
            byte[] classBytes = IOUtils.toByteArray(is);
            return containsMinecraftReference(classBytes);
        }
    }

    private static boolean containsMinecraftReference(byte[] classBytes) {
        // net.minecraft.client 在字节码中的表示形式
        String[] patterns = {
                "net/minecraft/client/",
                "Lnet/minecraft/client/"
        };

        String classContent = new String(classBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

        for (String pattern : patterns) {
            if (classContent.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
