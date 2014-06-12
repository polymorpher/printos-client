package potatos.client.util;

import javax.sound.sampled.*;

import java.io.IOException;
import java.net.URL;

public class SoundPlayer {
	public static void playSound(URL url) throws LineUnavailableException, UnsupportedAudioFileException, IOException {
//		Clip clip = AudioSystem.getClip();
		// getAudioInputStream() also accepts a File or InputStream
		AudioInputStream ais = AudioSystem.getAudioInputStream(url);
        AudioFormat format = ais.getFormat();
        DataLine.Info info = new DataLine.Info(Clip.class, format);
        Clip clip = (Clip)AudioSystem.getLine(info);
        clip.open(ais);
        clip.start();
//		clip.open(ais);
//		clip.loop(1);
	}
}
