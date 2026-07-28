import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

public class PngToWebp {
  public static void main(String[] args) throws Exception {
    File in = new File(args[0]);
    File out = new File(args[1]);
    float quality = args.length > 2 ? Float.parseFloat(args[2]) : 0.88f;
    BufferedImage img = ImageIO.read(in);
    if (img == null) throw new RuntimeException("Cannot read " + in);
    System.out.println("Input " + img.getWidth() + "x" + img.getHeight());
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
    if (!writers.hasNext()) writers = ImageIO.getImageWritersByFormatName("webp");
    if (!writers.hasNext()) {
      System.err.println("Available writers:");
      for (String s : ImageIO.getWriterFormatNames()) System.err.println("  " + s);
      throw new RuntimeException("No WebP writer");
    }
    ImageWriter writer = writers.next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    if (param.canWriteCompressed()) {
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      String[] types = param.getCompressionTypes();
      if (types != null && types.length > 0) {
        System.out.println("Compression types: " + String.join(",", types));
        String chosen = types[0];
        for (String t : types) {
          if (t.toLowerCase().contains("lossy")) chosen = t;
        }
        param.setCompressionType(chosen);
      }
      param.setCompressionQuality(quality);
    }
    try (FileImageOutputStream fos = new FileImageOutputStream(out)) {
      writer.setOutput(fos);
      writer.write(null, new IIOImage(img, null, null), param);
    }
    writer.dispose();
    System.out.println("Wrote " + out.getAbsolutePath() + " size=" + out.length());
  }
}