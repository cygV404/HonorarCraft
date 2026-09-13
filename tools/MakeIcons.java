import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Erzeugt .ico und .icns aus einer quadratischen PNG-Quelle. */
public class MakeIcons {

    static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    static void writeIco(BufferedImage src, int[] sizes, Path target) throws IOException {
        List<byte[]> blobs = new ArrayList<>();
        for (int s : sizes) blobs.add(png(scale(src, s)));

        int headerSize = 6 + 16 * sizes.length;
        int total = headerSize;
        for (byte[] b : blobs) total += b.length;

        ByteBuffer buf = ByteBuffer.allocate(total).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0);                    // reserviert
        buf.putShort((short) 1);                    // Typ 1 = Icon
        buf.putShort((short) sizes.length);

        int offset = headerSize;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            buf.put((byte) (s >= 256 ? 0 : s));     // 0 bedeutet 256
            buf.put((byte) (s >= 256 ? 0 : s));
            buf.put((byte) 0);                      // Farbpalette: keine
            buf.put((byte) 0);                      // reserviert
            buf.putShort((short) 1);                // Ebenen
            buf.putShort((short) 32);               // Bit pro Pixel
            buf.putInt(blobs.get(i).length);
            buf.putInt(offset);
            offset += blobs.get(i).length;
        }
        for (byte[] b : blobs) buf.put(b);
        Files.write(target, buf.array());
    }

    /** ICNS: Magic, Gesamtlänge, dann Blöcke aus Typ, Länge und PNG-Daten. */
    static void writeIcns(BufferedImage src, Path target) throws IOException {
        LinkedHashMap<String, Integer> entries = new LinkedHashMap<>();
        entries.put("icp4", 16);
        entries.put("icp5", 32);
        entries.put("ic07", 128);
        entries.put("ic08", 256);
        entries.put("ic09", 512);
        entries.put("ic11", 32);    // 16 @2x
        entries.put("ic12", 64);    // 32 @2x
        entries.put("ic13", 256);   // 128 @2x
        entries.put("ic14", 512);   // 256 @2x

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, Integer> e : entries.entrySet()) {
            byte[] data = png(scale(src, e.getValue()));
            body.write(e.getKey().getBytes("US-ASCII"));
            ByteBuffer len = ByteBuffer.allocate(4).order(java.nio.ByteOrder.BIG_ENDIAN);
            len.putInt(data.length + 8);
            body.write(len.array());
            body.write(data);
        }

        byte[] bodyBytes = body.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(8 + bodyBytes.length).order(java.nio.ByteOrder.BIG_ENDIAN);
        out.put("icns".getBytes("US-ASCII"));
        out.putInt(8 + bodyBytes.length);
        out.put(bodyBytes);
        Files.write(target, out.array());
    }

    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));
        Path dir = Paths.get(args[1]);
        System.out.println("Quelle: " + src.getWidth() + "x" + src.getHeight());

        writeIco(src, new int[]{16, 24, 32, 48, 64, 128, 256}, dir.resolve("iconWindows.ico"));
        writeIcns(src, dir.resolve("iconMacOS.icns"));
        Files.write(dir.resolve("iconDeb.png"), png(scale(src, 512)));

        for (String n : new String[]{"iconWindows.ico", "iconMacOS.icns", "iconDeb.png"}) {
            System.out.println(n + ": " + Files.size(dir.resolve(n)) + " Bytes");
        }
    }
}
