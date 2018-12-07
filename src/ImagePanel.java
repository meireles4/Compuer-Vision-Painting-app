import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ImagePanel extends Frame{
	
		private static final long serialVersionUID = 1L;
		private Image image; 
		
		// Construtor
		public ImagePanel(Image newImage)
		{
			image = newImage;

			// Handle close window button
			addWindowListener(new WindowAdapter() {
		  		public void windowClosing(WindowEvent e) {
		    		System.exit(0);
		  		}
			});
		}

		// Carregar nova imagem no ImagePanel
		public void newImage(Image im)
		{
			image = im;
			repaint();
		}
		
		// Desenhar imagem 
		public void paint(Graphics g) {
			g.drawImage(image, 0, 0, null);
			super.paint(g);
		}

}
