/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.NotNull
 */
package ink.ptms.chemdah.taboolib.common;

import ink.ptms.chemdah.taboolib.common.PrimitiveSettings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

public class PrimitiveIO {
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static final int BUFFER_SIZE = 8192;
    private static final ThreadLocal<MessageDigest> DIGEST_THREAD_LOCAL = ThreadLocal.withInitial(new Supplier<MessageDigest>(){

        @Override
        public MessageDigest get() {
            try {
                return MessageDigest.getInstance("SHA-1");
            }
            catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    });
    private static String runningFileName = "TabooLib";
    private static boolean isChineseEnvironment = true;
    private static final Logger logger;

    public static void debug(Object message2, Object ... args) {
        if (PrimitiveSettings.IS_DEBUG_MODE) {
            logger.log(Level.INFO, "[DEBUG] " + message2, args);
        }
    }

    public static void println(Object message2, Object ... args) {
        logger.log(Level.INFO, Objects.toString(message2), args);
    }

    public static void warning(Object message2, Object ... args) {
        logger.log(Level.WARNING, Objects.toString(message2), args);
    }

    public static void error(Object message2, Object ... args) {
        logger.log(Level.SEVERE, Objects.toString(message2), args);
    }

    public static boolean validation(File file, File hashFile) {
        return file.exists() && hashFile.exists() && PrimitiveIO.readFile(hashFile).startsWith(PrimitiveIO.getHash(file));
    }

    @NotNull
    public static String getHash(File file) {
        String string;
        block9: {
            MessageDigest digest = DIGEST_THREAD_LOCAL.get();
            digest.reset();
            InputStream inputStream = Files.newInputStream(file.toPath(), new OpenOption[0]);
            try {
                int total;
                byte[] buffer = new byte[8192];
                while ((total = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, total);
                }
                byte[] hashBytes = digest.digest();
                string = PrimitiveIO.bytesToHex(hashBytes);
                if (inputStream == null) break block9;
            }
            catch (Throwable throwable) {
                try {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                    return "null (" + UUID.randomUUID() + ")";
                }
            }
            inputStream.close();
        }
        return string;
    }

    public static String getHash(String data2) {
        MessageDigest digest = DIGEST_THREAD_LOCAL.get();
        digest.reset();
        digest.update(data2.getBytes(StandardCharsets.UTF_8));
        byte[] hashBytes = digest.digest();
        return PrimitiveIO.bytesToHex(hashBytes);
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0xF];
        }
        return new String(hexChars);
    }

    @NotNull
    public static String readFile(File file) {
        String string;
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            string = PrimitiveIO.readFully(fileInputStream, StandardCharsets.UTF_8);
        }
        catch (Throwable throwable) {
            try {
                try {
                    fileInputStream.close();
                }
                catch (Throwable throwable2) {
                    throwable.addSuppressed(throwable2);
                }
                throw throwable;
            }
            catch (IOException e) {
                e.printStackTrace();
                return "null (" + UUID.randomUUID() + ")";
            }
        }
        fileInputStream.close();
        return string;
    }

    @NotNull
    public static String readFully(InputStream inputStream, Charset charset) throws IOException {
        return new String(PrimitiveIO.readFully(inputStream), charset);
    }

    public static byte[] readFully(InputStream inputStream) throws IOException {
        int len;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while ((len = inputStream.read(buf)) > 0) {
            stream.write(buf, 0, len);
        }
        return stream.toByteArray();
    }

    @NotNull
    public static File copyFile(File from, File to) {
        try (FileInputStream fileIn = new FileInputStream(from);
             FileOutputStream fileOut = new FileOutputStream(to);
             FileChannel channelIn = fileIn.getChannel();
             FileChannel channelOut = fileOut.getChannel();){
            channelIn.transferTo(0L, channelIn.size(), channelOut);
        }
        catch (IOException t) {
            t.printStackTrace();
        }
        return to;
    }

    public static void downloadFile(URL url, File out) throws IOException {
        int len;
        out.getParentFile().mkdirs();
        InputStream ins = url.openStream();
        OutputStream outs = Files.newOutputStream(out.toPath(), new OpenOption[0]);
        byte[] buffer = new byte[8192];
        while ((len = ins.read(buffer)) > 0) {
            outs.write(buffer, 0, len);
        }
        outs.close();
        ins.close();
    }

    public static String getRunningFileName() {
        return runningFileName;
    }

    public static boolean isChineseEnvironment() {
        return isChineseEnvironment;
    }

    public static String t(String zh, String en) {
        if (isChineseEnvironment) {
            return zh;
        }
        return en;
    }

    static {
        try {
            isChineseEnvironment = Locale.getDefault().toLanguageTag().startsWith("zh");
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            runningFileName = new File(PrimitiveIO.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getName();
            runningFileName = runningFileName.startsWith("common-") ? "App" : runningFileName.substring(0, runningFileName.lastIndexOf("."));
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        logger = Logger.getLogger(runningFileName);
    }
}

