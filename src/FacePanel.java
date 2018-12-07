import java.awt.*;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public class FacePanel extends JPanel{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private BufferedImage image;
	int count = 0;
	
	public FacePanel() {
		super();
	}
	
	public void setFace(BufferedImage img) {
		image = img;
	}
	
	public void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		
		if(this.image == null) {
			System.out.println("!! The JPanel image is null !!");
			return;
		}
		g.drawImage(this.image, 10, 10, this.image.getWidth(), this.image.getHeight(), null);
		g.setFont(new Font("arial", 2,20));
		g.setColor(Color.WHITE);
		g.drawString("Web cam is processing frame by frame [ Frame: " + (count++) + " ]", 50, 50);
	}
}
